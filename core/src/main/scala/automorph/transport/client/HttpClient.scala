package automorph.transport.client

import automorph.log.{LogProperties, Logger, Logging, MessageLog}
import automorph.spi.EffectSystem.Completable
import automorph.spi.{ClientTransport, EffectSystem}
import automorph.transport.HttpClientBase.{
  completableEffect, overrideUrl, webSocketCloseReason, webSocketCloseStatusCode, webSocketConnectionClosed,
  webSocketSchemePrefix, webSocketUnexpectedMessage,
}
import automorph.transport.client.HttpClient.{Context, FrameListener, Response, Transport}
import automorph.transport.{ConnectionPool, HttpContext, HttpMethod, Protocol}
import automorph.util.Extensions.{ByteArrayOps, ByteBufferOps, EffectOps}
import java.net.URI
import java.net.http.HttpClient.Builder
import java.net.http.HttpRequest.BodyPublishers
import java.net.http.HttpResponse.BodyHandlers
import java.net.http.WebSocket.Listener
import java.net.http.{HttpRequest, WebSocket}
import java.nio.ByteBuffer
import java.util.concurrent.CompletionStage
import scala.collection.immutable.ListMap
import scala.collection.mutable.ArrayBuffer
import scala.jdk.CollectionConverters.{ListHasAsScala, MapHasAsScala}
import scala.jdk.OptionConverters.RichOptional
import scala.util.Try

/**
 * Standard JRE HttpClient HTTP & WebSocket client message transport plugin.
 *
 * Uses the supplied RPC request as HTTP request body and returns HTTP response body as a result.
 *
 * @see
 *   [[https://en.wikipedia.org/wiki/HTTP Transport protocol]]
 * @see
 *   [[https://en.wikipedia.org/wiki/WebSocket Alternative transport protocol]]
 * @see
 *   [[https://openjdk.org/groups/net/httpclient/intro.html documentation]]
 * @see
 *   [[https://openjdk.org/groups/net/httpclient/intro.html API]]
 * @constructor
 *   Creates an HttpClient HTTP & WebSocket message client transport plugin.
 * @param effectSystem
 *   effect system plugin
 * @param url
 *   remote API HTTP or WebSocket URL
 * @param method
 *   HTTP request method (default: POST)
 * @param builder
 *   HttpClient builder (default: empty)
 * @tparam Effect
 *   effect type
 */
final case class HttpClient[Effect[_]](
  effectSystem: EffectSystem[Effect],
  url: URI,
  method: HttpMethod = HttpMethod.Post,
  builder: Builder = HttpClient.builder,
) extends ClientTransport[Effect, Context] with Logging {

  private type Request = Either[HttpRequest, (WebSocket.Builder, Array[Byte])]

  private val contentTypeHeader = "Content-Type"
  private val acceptHeader = "Accept"
  private val httpEmptyUrl = new URI("http://empty")
  private val httpMethods = HttpMethod.values.map(_.name).toSet
  private val httpClient = builder.build
  private val webSocketConnectionPool = {
    val maxPeerConnections = Option(System.getProperty("jdk.httpclient.connectionPoolSize")).flatMap(_.toIntOption)
    ConnectionPool(Some(openWebSocket), closeWebSocket, maxPeerConnections, Protocol.WebSocket, effectSystem, logger)
  }
  private val log = MessageLog(logger, Protocol.Http.name)
  implicit private val system: EffectSystem[Effect] = effectSystem

  override def call(
    body: Array[Byte],
    context: Context,
    id: String,
    mediaType: String,
  ): Effect[(Array[Byte], Context)] = {
    // Create the request
    val (request, requestUrl, protocol) = createRequest(body, mediaType, context)
    lazy val responseProperties = ListMap(
      LogProperties.requestId -> id,
      LogProperties.url -> requestUrl.toString,
    )

    // Send the request and process the response
    send(request, requestUrl, id, protocol).flatFold(
      { error =>
        log.failedReceiveResponse(error, responseProperties, protocol.name)
        effectSystem.failed(error)
      },
      { response =>
        lazy val allProperties = responseProperties ++ response.statusCode.map(LogProperties.status -> _.toString)
        log.receivedResponse(allProperties, protocol.name)
        effectSystem.successful(response.body -> responseContext(response))
      },
    )
  }

  override def tell(
    body: Array[Byte],
    context: Context,
    id: String,
    mediaType: String,
  ): Effect[Unit] = {
    val (request, requestUrl, protocol) = createRequest(body, mediaType, context)
    send(request, requestUrl, id, protocol).map(_ => ())
  }

  override def context: Context =
    Transport.context.url(url).method(method)

  override def init(): Effect[Unit] =
    webSocketConnectionPool.init()

  override def close(): Effect[Unit] =
    webSocketConnectionPool.close()

  private def responseContext(response: Response): Context =
    response.statusCode.map(context.statusCode).getOrElse(context).headers(response.headers*)

  private def send(request: Request, requestUrl: URI, requestId: String, protocol: Protocol): Effect[Response] = {
    lazy val requestProperties = ListMap(
      LogProperties.requestId -> requestId,
      LogProperties.url -> requestUrl.toString,
    ) ++ request.swap.toOption.map(httpRequest => LogProperties.method -> httpRequest.method)
    log.sendingRequest(requestProperties, protocol.name)
    request.fold(sendHttp, webSocketRequest => sendWebSocket(webSocketRequest, requestUrl)).flatFold(
      error => {
        log.failedSendRequest(error, requestProperties, protocol.name)
        effectSystem.failed(error)
      },
      response => {
        log.sentRequest(requestProperties, protocol.name)
        effectSystem.successful(response)
      },
    )
  }

  private def sendHttp(httpRequest: HttpRequest): Effect[Response] =
    completableEffect(httpClient.sendAsync(httpRequest, BodyHandlers.ofByteArray), effectSystem).map { response =>
      val headers = response
        .headers.map.asScala.toSeq
        .flatMap { case (name, values) => values.asScala.map(name -> _) }
      Response(response.body, Some(response.statusCode), headers)
    }

  private def sendWebSocket(request: (WebSocket.Builder, Array[Byte]), requestUrl: URI): Effect[Response] = {
    val (webSocketBuilder, requestBody) = request
    effectSystem.completable[Response].flatMap { expectedResponse =>
      webSocketConnectionPool.using(
        requestUrl.toString,
        (webSocketBuilder, requestUrl),
        { case (webSocket, frameListener) =>
          frameListener.expectedResponse = Some(expectedResponse)
          completableEffect(webSocket.sendBinary(requestBody.toByteBuffer, true), effectSystem)
            .flatMap(_ => expectedResponse.effect)
        },
      )
    }
  }

  private def createRequest(
    requestBody: Array[Byte],
    mediaType: String,
    requestContext: Context,
  ): (Request, URI, Protocol) = {
    val baseUrl = requestContext.transportContext
      .flatMap(transport => Try(transport.request.build).toOption)
      .map(_.uri).getOrElse(url)
    val requestUrl = overrideUrl(baseUrl, requestContext)
    requestUrl.getScheme.toLowerCase match {
      case scheme if scheme.startsWith(webSocketSchemePrefix) =>
        // Create WebSocket request
        val webSocketBuilder = createWebSocketBuilder(requestContext)
        (Right((webSocketBuilder, requestBody)), requestUrl, Protocol.WebSocket)
      case _ =>
        // Create HTTP request
        val httpRequest = createHttpRequest(requestBody, requestUrl, mediaType, requestContext)
        (Left(httpRequest), httpRequest.uri, Protocol.WebSocket)
    }
  }

  private def createHttpRequest(
    requestBody: Array[Byte],
    requestUrl: URI,
    mediaType: String,
    httpContext: Context,
  ): HttpRequest = {
    // Method & body
    val requestBuilder = httpContext.transportContext.map(_.request).getOrElse(HttpRequest.newBuilder)
    val transportRequest = Try(requestBuilder.build).toOption
    val requestMethod = httpContext.method.map(_.name).getOrElse(method.name)
    require(httpMethods.contains(requestMethod), s"Invalid HTTP method: $requestMethod")
    val methodBuilder = requestBuilder
      .uri(requestUrl)
      .method(requestMethod, BodyPublishers.ofByteArray(requestBody))

    // Headers
    val headers = httpContext.headers.flatMap { case (name, value) => Seq(name, value) }
    val headersBuilder = (headers match {
      case Seq() => methodBuilder
      case values => methodBuilder.headers(values.toArray*)
    }).header(contentTypeHeader, mediaType).header(acceptHeader, mediaType)

    // Timeout
    httpContext.timeout
      .map(timeout => java.time.Duration.ofMillis(timeout.toMillis))
      .orElse(transportRequest.flatMap(_.timeout.toScala))
      .map(timeout => headersBuilder.timeout(timeout))
      .getOrElse(headersBuilder).build
  }

  private def createWebSocketBuilder(httpContext: Context): WebSocket.Builder = {
    // Headers
    val transportBuilder = httpContext.transportContext.map(_.request).getOrElse(HttpRequest.newBuilder)
    val headers = transportBuilder
      .uri(httpEmptyUrl).build.headers.map.asScala.toSeq
      .flatMap { case (name, values) =>
        values.asScala.map(name -> _)
      } ++ httpContext.headers
    val headersBuilder = LazyList.iterate(httpClient.newWebSocketBuilder -> headers) { case (builder, headers) =>
      headers
        .headOption.map { case (name, value) => builder.header(name, value) -> headers.tail }
        .getOrElse(builder -> headers)
    }.dropWhile(_._2.nonEmpty).headOption.map(_._1).getOrElse(httpClient.newWebSocketBuilder)

    // Timeout
    httpClient.connectTimeout.toScala.map(headersBuilder.connectTimeout).getOrElse(headersBuilder)
  }

  private def openWebSocket(endpoint: (WebSocket.Builder, URI)): Effect[(WebSocket, FrameListener[Effect])] = {
    val (webSocketBuilder, requestUrl) = endpoint
    val frameListener = FrameListener(requestUrl, effectSystem, logger)
    completableEffect(webSocketBuilder.buildAsync(requestUrl, frameListener), effectSystem).map(_ -> frameListener)
  }

  private def closeWebSocket(connection: (WebSocket, FrameListener[Effect])): Effect[Unit] =
    completableEffect(
      connection._1.sendClose(webSocketCloseStatusCode, webSocketCloseReason),
      effectSystem,
    ).map(_ => ())
}

object HttpClient {

  /** Message context type. */
  type Context = HttpContext[Transport]

  /** Default HTTP client builder. */
  val builder: Builder = java.net.http.HttpClient.newBuilder

  /** Transport-specific context. */
  final case class Transport(request: HttpRequest.Builder)

  final private case class Response(
    body: Array[Byte],
    statusCode: Option[Int],
    headers: Seq[(String, String)],
  )

  final private case class FrameListener[Effect[_]](
    url: URI,
    effectSystem: EffectSystem[Effect],
    logger: Logger,
    var expectedResponse: Option[Completable[Effect, Response]] = None,
  ) extends Listener {

    private val buffers = ArrayBuffer.empty[ByteBuffer]

    override def onBinary(webSocket: WebSocket, data: ByteBuffer, last: Boolean): CompletionStage[?] = {
      buffers += data
      if (last) {
        val responseBody = buffers match {
          case ArrayBuffer(buffer) => buffer.toByteArray
          case _ =>
            val response = ByteBuffer.allocate(buffers.map(_.capacity).sum)
            buffers.foreach(response.put)
            response.toByteArray
        }
        buffers.clear()
        expectedResponse.map { response =>
          expectedResponse = None
          effectSystem.runAsync(response.succeed(Response(responseBody, None, Seq.empty)))
        }.getOrElse(logger.error(webSocketUnexpectedMessage, Map(LogProperties.url -> url)))
      }
      super.onBinary(webSocket, data, last)
    }

    override def onError(webSocket: WebSocket, error: Throwable): Unit = {
      expectedResponse.map { response =>
        expectedResponse = None
        effectSystem.runAsync(response.fail(error))
      }.getOrElse(logger.error(webSocketUnexpectedMessage, Map(LogProperties.url -> url)))
      super.onError(webSocket, error)
    }

    override def onClose(webSocket: WebSocket, statusCode: Int, reason: String): CompletionStage[?] = {
      expectedResponse.foreach { response =>
        expectedResponse = None
        effectSystem.runAsync(response.fail(new IllegalStateException(webSocketConnectionClosed)))
      }
      super.onClose(webSocket, statusCode, reason)
    }
  }

  object Transport {

    /** Implicit default context value. */
    implicit val context: HttpContext[Transport] = HttpContext()
  }
}
