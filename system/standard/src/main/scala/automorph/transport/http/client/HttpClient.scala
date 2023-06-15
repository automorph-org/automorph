package automorph.transport.http.client

import automorph.log.{LogProperties, Logging, MessageLog}
import automorph.spi.AsyncEffectSystem.Completable
import automorph.spi.{AsyncEffectSystem, ClientTransport, EffectSystem}
import automorph.transport.http.client.HttpClient.{Context, Response, TransportContext, defaultBuilder}
import automorph.transport.http.{HttpContext, HttpMethod, Protocol}
import automorph.util.Extensions.{ByteArrayOps, ByteBufferOps, EffectOps}
import java.net.URI
import java.net.http.HttpClient.Builder
import java.net.http.HttpRequest.BodyPublishers
import java.net.http.HttpResponse.BodyHandlers
import java.net.http.WebSocket.Listener
import java.net.http.{HttpRequest, HttpResponse, WebSocket}
import java.nio.ByteBuffer
import java.util.concurrent.{CompletableFuture, CompletionStage}
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
 *   [[https://docs.oracle.com/en/java/javase/19/docs/api/java.net.http/java/net/http/HttpClient.html API]]
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
  builder: Builder = defaultBuilder,
) extends ClientTransport[Effect, Context] with Logging {

  private val contentTypeHeader = "Content-Type"
  private val acceptHeader = "Accept"
  private val httpMethods = HttpMethod.values.map(_.name).toSet
  private val httpEmptyUrl = new URI("http://empty")
  private val webSocketsSchemePrefix = "ws"
  private val httpClient = builder.build
  private val log = MessageLog(logger, Protocol.Http.name)
  private implicit val system: EffectSystem[Effect] = effectSystem

  override def call(
    requestBody: Array[Byte],
    requestContext: Context,
    requestId: String,
    mediaType: String,
  ): Effect[(Array[Byte], Context)] =
    // Send the request
    createRequest(requestBody, mediaType, requestContext).flatMap { case (request, requestUrl) =>
      val protocol = request.fold(_ => Protocol.Http, _ => Protocol.WebSocket)
      send(request, requestUrl, requestId, protocol).either.flatMap { result =>
        lazy val responseProperties = ListMap(LogProperties.requestId -> requestId, "URL" -> requestUrl.toString)

        // Process the response
        result.fold(
          error => {
            log.failedReceiveResponse(error, responseProperties, protocol.name)
            effectSystem.failed(error)
          },
          response => {
            log.receivedResponse(responseProperties ++ response.statusCode.map("Status" -> _.toString), protocol.name)
            effectSystem.successful(response.body -> getResponseContext(response))
          },
        )
      }
    }

  override def tell(
    requestBody: Array[Byte],
    requestContext: Context,
    requestId: String,
    mediaType: String,
  ): Effect[Unit] =
    createRequest(requestBody, mediaType, requestContext).flatMap { case (request, requestUrl) =>
      val protocol = request.fold(_ => Protocol.Http, _ => Protocol.WebSocket)
      send(request, requestUrl, requestId, protocol).map(_ => ())
    }

  override def context: Context =
    TransportContext.defaultContext.url(url).method(method)

  override def init(): Effect[Unit] =
    effectSystem.successful{}

  override def close(): Effect[Unit] =
    effectSystem.successful{}

  private def getResponseContext(response: Response): Context = {
    response.statusCode.map(context.statusCode).getOrElse(context).headers(response.headers*)
  }

  private def send(
    request: Either[HttpRequest, (Effect[WebSocket], Effect[Response], Array[Byte])],
    requestUrl: URI,
    requestId: String,
    protocol: Protocol,
  ): Effect[Response] = {
    lazy val requestProperties = ListMap(
      LogProperties.requestId -> requestId,
      "URL" -> requestUrl.toString
    ) ++ request.swap.toOption.map(httpRequest => "Method" -> httpRequest.method)
    log.sendingRequest(requestProperties, protocol.name)
    request.fold(
      // Send HTTP request
      httpRequest => sendHttp(httpRequest),
      // Send WebSocket request
      { case (webSocketEffect, resultEffect, requestBody) =>
        sendWebSocket(webSocketEffect, resultEffect, requestBody)
      },
    ).either.flatMap(
      _.fold(
        error => {
          log.failedSendRequest(error, requestProperties, protocol.name)
          effectSystem.failed(error)
        },
        response => {
          log.sentRequest(requestProperties, protocol.name)
          effectSystem.successful(response)
        },
      )
    )
  }

  private def sendHttp(httpRequest: HttpRequest): Effect[Response] =
    effectSystem match {
      case completableSystem: AsyncEffectSystem[?] =>
        effect(
          httpClient.sendAsync(httpRequest, BodyHandlers.ofByteArray),
          completableSystem.asInstanceOf[AsyncEffectSystem[Effect]],
        ).map(httpResponse)
      case _ => effectSystem.evaluate(httpResponse(httpClient.send(httpRequest, BodyHandlers.ofByteArray)))
    }

  private def sendWebSocket(
    webSocketEffect: Effect[WebSocket],
    resultEffect: Effect[Response],
    requestBody: Array[Byte],
  ): Effect[Response] =
    withCompletable(asyncSystem =>
      webSocketEffect.flatMap { webSocket =>
        effect(webSocket.sendBinary(requestBody.toByteBuffer, true), asyncSystem).flatMap(_ => resultEffect)
      }
    )

  private def httpResponse(response: HttpResponse[Array[Byte]]): Response = {
    val headers = response.headers.map.asScala.toSeq.flatMap { case (name, values) => values.asScala.map(name -> _) }
    Response(response.body, Some(response.statusCode), headers)
  }

  private def withCompletable[T](function: AsyncEffectSystem[Effect] => Effect[T]): Effect[T] =
    effectSystem match {
      case completableSystem: AsyncEffectSystem[?] =>
        function(completableSystem.asInstanceOf[AsyncEffectSystem[Effect]])
      case _ => effectSystem.failed(new IllegalArgumentException(
        s"""WebSocket protocol not available for effect system
           | not supporting completable effects: ${effectSystem.getClass.getName}""".stripMargin
      ))
    }

  private def effect[T](
    completableFuture: => CompletableFuture[T],
    completableSystem: AsyncEffectSystem[Effect],
  ): Effect[T] =
    completableSystem.completable[T].flatMap { completable =>
      Try(completableFuture).fold(
        exception => completable.fail(exception).runAsync,
        value => {
          value.handle { case (result, error) =>
            Option(result).map(value => completable.succeed(value).runAsync).getOrElse {
              completable.fail(Option(error).getOrElse(new IllegalStateException("Missing completable future result")))
                .runAsync
            }
          }
          ()
        },
      )
      completable.effect
    }

  private def createRequest(
    requestBody: Array[Byte],
    mediaType: String,
    requestContext: Context,
  ): Effect[(Either[HttpRequest, (Effect[WebSocket], Effect[Response], Array[Byte])], URI)] = {
    val requestUrl = requestContext.overrideUrl {
       requestContext.transportContext.flatMap { transport =>
         Try(transport.request.build).toOption
       }.map(_.uri).getOrElse(url)
    }
    requestUrl.getScheme.toLowerCase match {
      case scheme if scheme.startsWith(webSocketsSchemePrefix) =>
        // Create WebSocket request
        withCompletable(completableSystem =>
          effectSystem.evaluate {
            val completableResponse = completableSystem.completable[Response]
            val response = completableResponse.flatMap(_.effect)
            val webSocketBuilder = createWebSocketBuilder( requestContext)
            val webSocket = connectWebSocket(webSocketBuilder, requestUrl, completableResponse, completableSystem)
            Right((webSocket, response, requestBody)) -> requestUrl
          }
        )
      case _ =>
        // Create HTTP request
        effectSystem.evaluate {
          val httpRequest = createHttpRequest(requestBody, requestUrl, mediaType,  requestContext)
          Left(httpRequest) -> httpRequest.uri
        }
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
    val methodBuilder = requestBuilder.uri(requestUrl)
      .method(requestMethod, BodyPublishers.ofByteArray(requestBody))

    // Headers
    val headers = httpContext.headers.flatMap { case (name, value) => Seq(name, value) }
    val headersBuilder = (headers match {
      case Seq() => methodBuilder
      case values => methodBuilder.headers(values.toArray*)
    }).header(contentTypeHeader, mediaType).header(acceptHeader, mediaType)

    // Timeout
    httpContext.timeout.map(timeout => java.time.Duration.ofMillis(timeout.toMillis))
      .orElse(transportRequest.flatMap(_.timeout.toScala)).map(timeout => headersBuilder.timeout(timeout))
      .getOrElse(headersBuilder).build
  }

  private def connectWebSocket(
    builder: WebSocket.Builder,
    requestUrl: URI,
    responseEffect: Effect[Completable[Effect, Response]],
    completableSystem: AsyncEffectSystem[Effect],
  ): Effect[WebSocket] =
    responseEffect.flatMap(response =>
      effect(builder.buildAsync(requestUrl, webSocketListener(response)), completableSystem)
    )

  private def webSocketListener(response: Completable[Effect, Response]) =
    new Listener {

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
          response.succeed(Response(responseBody, None, Seq.empty)).runAsync
        }
        super.onBinary(webSocket, data, last)
      }

      override def onClose(webSocket: WebSocket, statusCode: Int, reason: String): CompletionStage[?] =
        super.onClose(webSocket, statusCode, reason)

      override def onError(webSocket: WebSocket, error: Throwable): Unit = {
        response.fail(error)
        super.onError(webSocket, error)
      }
    }

  private def createWebSocketBuilder(httpContext: Context): WebSocket.Builder = {
    // Headers
    val transportBuilder = httpContext.transportContext.map(_.request).getOrElse(HttpRequest.newBuilder)
    val headers = transportBuilder.uri(httpEmptyUrl).build.headers.map.asScala.toSeq.flatMap { case (name, values) =>
      values.asScala.map(name -> _)
    } ++ httpContext.headers
    val headersBuilder = LazyList.iterate(httpClient.newWebSocketBuilder -> headers) { case (builder, headers) =>
      headers.headOption.map { case (name, value) => builder.header(name, value) -> headers.tail }
        .getOrElse(builder -> headers)
    }.dropWhile(_._2.nonEmpty).headOption.map(_._1).getOrElse(httpClient.newWebSocketBuilder)

    // Timeout
    httpClient.connectTimeout.toScala.map(headersBuilder.connectTimeout).getOrElse(headersBuilder)
  }
}

object HttpClient {

  /** Message context type. */
  type Context = HttpContext[TransportContext]

  /** Default HTTP client builder. */
  val defaultBuilder: Builder = java.net.http.HttpClient.newBuilder

  /** Transport context. */
  final case class TransportContext(request: HttpRequest.Builder)

  object TransportContext {

    /** Implicit default context value. */
    implicit val defaultContext: HttpContext[TransportContext] = HttpContext()
  }

  private final case class Response(
    body: Array[Byte],
    statusCode: Option[Int],
    headers: Seq[(String, String)]
  )
}
