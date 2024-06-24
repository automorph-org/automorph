package automorph.transport.client

import automorph.log.{LogProperties, Logger, Logging}
import automorph.spi.EffectSystem.Completable
import automorph.spi.{ClientTransport, EffectSystem}
import automorph.transport.HttpClientBase.{completableEffect, overrideUrl, webSocketCloseReason, webSocketCloseStatusCode, webSocketConnectionClosed, webSocketSchemePrefix, webSocketUnexpectedMessage}
import automorph.transport.client.HttpClient.{Context, FrameListener, Transport}
import automorph.transport.{ClientServerHttpSender, ConnectionPool, HttpContext, HttpListen, HttpMethod, Protocol}
import automorph.util.Extensions.{ByteArrayOps, ByteBufferOps, EffectOps}
import java.net.URI
import java.net.http.HttpClient.Builder
import java.net.http.HttpRequest.BodyPublishers
import java.net.http.HttpResponse.BodyHandlers
import java.net.http.WebSocket.Listener
import java.net.http.{HttpRequest, WebSocket}
import java.nio.ByteBuffer
import java.time.Duration
import java.util.concurrent.CompletionStage
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
 * @param listen
 *   listen for RPC requests from the server settings (default: disabled)
 * @param builder
 *   HttpClient builder (default: empty with5 seconds connect timeout)
 * @tparam Effect
 *   effect type
 */
final case class HttpClient[Effect[_]](
  effectSystem: EffectSystem[Effect],
  url: URI,
  method: HttpMethod = HttpMethod.Post,
  listen: HttpListen = HttpListen(),
  builder: Builder = HttpClient.builder,
) extends ClientTransport[Effect, Context] with Logging {

  private type Request = Either[HttpRequest, (Array[Byte], WebSocket.Builder, URI)]

  private val contentTypeHeader = "Content-Type"
  private val acceptHeader = "Accept"
  private val httpEmptyUrl = new URI("http://empty")
  private val httpMethods = HttpMethod.values.map(_.name).toSet
  private val httpClient = builder.build
  private val webSocketConnectionPool = {
    val maxPeerConnections = Option(System.getProperty("jdk.httpclient.connectionPoolSize")).flatMap(_.toIntOption)
    ConnectionPool(Some(openWebSocket), closeWebSocket, Protocol.WebSocket, effectSystem, maxPeerConnections)
  }
  private val sender = ClientServerHttpSender(createRequest, sendRequest, url, method, listen, effectSystem)
  implicit private val system: EffectSystem[Effect] = effectSystem

  override def call(
    body: Array[Byte],
    context: Context,
    id: String,
    mediaType: String,
  ): Effect[(Array[Byte], Context)] =
    sender.call(body, context, id, mediaType)

  override def tell(
    body: Array[Byte],
    context: Context,
    id: String,
    mediaType: String,
  ): Effect[Unit] =
    sender.tell(body, context, id, mediaType)

  override def context: Context =
    Transport.context.url(url).method(method)

  override def init(): Effect[Unit] =
    webSocketConnectionPool.init().map(_ => sender.listen())

  override def close(): Effect[Unit] =
    webSocketConnectionPool.close()

  private def createRequest(
    requestBody: Array[Byte],
    context: Context,
    contentType: String,
  ): (Request, Context, Protocol) = {
    val baseUrl = context.transportContext
      .flatMap(transport => Try(transport.request.build).toOption)
      .map(_.uri).getOrElse(url)
    val requestUrl = overrideUrl(baseUrl, context)
    requestUrl.getScheme.toLowerCase match {
      case scheme if scheme.startsWith(webSocketSchemePrefix) =>
        val webSocketBuilder = createWebSocketBuilder(context, contentType)
        (Right((requestBody, webSocketBuilder, requestUrl)), context.url(requestUrl), Protocol.WebSocket)
      case _ =>
        val httpRequest = createHttpRequest(requestBody, requestUrl, contentType, context)
        val requestContext = context.url(httpRequest.uri).method(HttpMethod.valueOf(httpRequest.method))
        (Left(httpRequest), requestContext, Protocol.Http)
    }
  }

  private def sendRequest(request: Request, requestContext: Context): Effect[(Array[Byte], Context)] =
    request.fold(
      httpRequest =>
        completableEffect(httpClient.sendAsync(httpRequest, BodyHandlers.ofByteArray), effectSystem).map { response =>
          val headers = response
            .headers.map.asScala.toSeq
            .flatMap { case (name, values) => values.asScala.map(name -> _) }
          response.body -> requestContext.statusCode(response.statusCode).headers(headers*)
        },
      { case (requestBody, webSocketBuilder, requestUrl) =>
        effectSystem.completable[Array[Byte]].flatMap { expectedResponse =>
          webSocketConnectionPool.using(
            requestUrl.toString,
            (webSocketBuilder, requestUrl),
            { case (webSocket, frameListener) =>
              frameListener.expectedResponse = Some(expectedResponse)
              completableEffect(webSocket.sendBinary(requestBody.toByteBuffer, true), effectSystem)
                .flatMap(_ => expectedResponse.effect.map(_ -> requestContext.headers(Seq.empty[(String, String)]*)))
            },
          )
        }
      },
    )

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

  private def createWebSocketBuilder(httpContext: Context, contentType: String): WebSocket.Builder = {
    // Headers
    val transportBuilder = httpContext.transportContext.map(_.request).getOrElse(HttpRequest.newBuilder)
    val headers = transportBuilder
      .uri(httpEmptyUrl).build.headers.map.asScala.toSeq
      .flatMap { case (name, values) =>
        values.asScala.map(name -> _)
      } ++ httpContext.headers ++ Seq(contentTypeHeader -> contentType, acceptHeader -> contentType)
    val headersBuilder = LazyList.iterate(httpClient.newWebSocketBuilder -> headers) { case (builder, headers) =>
      headers
        .headOption.map { case (name, value) => builder.header(name, value) -> headers.tail }
        .getOrElse(builder -> headers)
    }.dropWhile(_._2.nonEmpty).headOption.map(_._1).getOrElse(httpClient.newWebSocketBuilder)

    // Timeout
    httpClient.connectTimeout.toScala.map(headersBuilder.connectTimeout).getOrElse(headersBuilder)
  }

  private def openWebSocket(
    endpoint: (WebSocket.Builder, URI),
    connectionId: Int,
  ): Effect[(WebSocket, FrameListener[Effect])] = {
    val (webSocketBuilder, requestUrl) = endpoint
    val removeConnection = () => webSocketConnectionPool.remove(requestUrl.toString, connectionId)
    val frameListener = FrameListener(requestUrl, removeConnection, effectSystem, logger)
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
  val builder: Builder = java.net.http.HttpClient.newBuilder.connectTimeout(Duration.ofSeconds(5))

  /** Transport-specific context. */
  final case class Transport(request: HttpRequest.Builder)

  final private case class FrameListener[Effect[_]](
    url: URI,
    removeConnection: () => Unit,
    effectSystem: EffectSystem[Effect],
    logger: Logger,
    var expectedResponse: Option[Completable[Effect, Array[Byte]]] = None,
  ) extends Listener {
    implicit val system: EffectSystem[Effect] = effectSystem
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
          response.succeed(responseBody).runAsync
        }.getOrElse(logger.error(webSocketUnexpectedMessage, Map(LogProperties.url -> url)))
      }
      super.onBinary(webSocket, data, last)
    }

    override def onError(webSocket: WebSocket, error: Throwable): Unit = {
      removeConnection()
      expectedResponse.map { response =>
        expectedResponse = None
        response.fail(error).runAsync
      }.getOrElse(logger.error(webSocketUnexpectedMessage, Map(LogProperties.url -> url)))
      super.onError(webSocket, error)
    }

    override def onClose(webSocket: WebSocket, statusCode: Int, reason: String): CompletionStage[?] = {
      removeConnection()
      expectedResponse.foreach { response =>
        expectedResponse = None
        response.fail(new IllegalStateException(webSocketConnectionClosed)).runAsync
      }
      super.onClose(webSocket, statusCode, reason)
    }
  }

  object Transport {

    /** Implicit default context value. */
    implicit val context: HttpContext[Transport] = HttpContext()
  }
}
