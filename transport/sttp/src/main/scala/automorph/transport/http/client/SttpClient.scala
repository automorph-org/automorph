package automorph.transport.http.client

import automorph.log.{LogProperties, Logging, MessageLog}
import automorph.spi.{ClientTransport, EffectSystem}
import automorph.transport.http.client.SttpClient.{Context, TransportContext}
import automorph.transport.http.{HttpContext, HttpMethod, Protocol}
import automorph.util.Extensions.EffectOps
import java.net.URI
import scala.collection.immutable.ListMap
import sttp.capabilities.WebSockets
import sttp.client3.{
  PartialRequest, Request, Response, SttpBackend, asByteArrayAlways, asWebSocketAlways, basicRequest, ignore
}
import sttp.model.{Header, MediaType, Method, Uri}

/**
 * STTP HTTP & WebSocket client message transport plugin.
 *
 * Uses the supplied RPC request as HTTP request body and returns HTTP response body as a result.
 *
 * @see
 *   [[https://en.wikipedia.org/wiki/HTTP Transport protocol]]
 * @see
 *   [[https://en.wikipedia.org/wiki/WebSocket Alternative transport protocol]]
 * @see
 *   [[https://sttp.softwaremill.com/en/latest Library documentation]]
 * @see
 *   [[https://javadoc.io/doc/com.softwaremill.sttp.client3/core_3/latest/index.html API]]
 * @constructor
 *   Creates an STTP HTTP & WebSocket client message transport plugin with the specified STTP backend.
 * @param effectSystem
 *   effect system plugin
 * @param backend
 *   STTP backend
 * @param url
 *   remote API HTTP or WebSocket URL
 * @param method
 *   HTTP request method
 * @tparam Effect
 *   effect type
 */
final case class SttpClient[Effect[_]] private (
  effectSystem: EffectSystem[Effect],
  backend: SttpBackend[Effect, ?],
  url: URI,
  method: HttpMethod,
  webSocket: Boolean,
) extends ClientTransport[Effect, Context] with Logging {

  private type WebSocket = sttp.capabilities.Effect[Effect] & WebSockets

  private val webSocketsSchemePrefix = "ws"
  private val defaultUrl = Uri(url).toJavaUri
  private val log = MessageLog(logger, Protocol.Http.name)
  private implicit val system: EffectSystem[Effect] = effectSystem

  override def call(
    requestBody: Array[Byte],
    requestContext: Context,
    requestId: String,
    mediaType: String,
  ): Effect[(Array[Byte], Context)] = {
    // Send the request
    val sttpRequest = createRequest(requestBody, mediaType, requestContext)
    transportProtocol(sttpRequest).flatMap { protocol =>
      send(sttpRequest, requestId, protocol).either.flatMap { result =>
        lazy val responseProperties = ListMap(
          LogProperties.requestId -> requestId,
          "URL" -> sttpRequest.uri.toString
        )

        // Process the response
        result.fold(
          error => {
            log.failedReceiveResponse(error, responseProperties, protocol.name)
            effectSystem.failed(error)
          },
          response => {
            log.receivedResponse(responseProperties + ("Status" -> response.code.toString), protocol.name)
            effectSystem.successful(response.body.toArray[Byte] -> getResponseContext(response))
          },
        )
      }
    }
  }

  override def tell(
    requestBody: Array[Byte],
    requestContext: Context,
    requestId: String,
    mediaType: String,
  ): Effect[Unit] = {
    val sttpRequest = createRequest(requestBody, mediaType, requestContext)
    transportProtocol(sttpRequest).flatMap {
      case Protocol.Http => send(sttpRequest.response(ignore), requestId, Protocol.Http).map(_ => ())
      case Protocol.WebSocket => send(sttpRequest, requestId, Protocol.WebSocket).map(_ => ())
    }
  }

  override def context: Context =
    TransportContext.defaultContext.url(url).method(method)

  override def init(): Effect[Unit] =
    effectSystem.successful {}

  override def close(): Effect[Unit] =
    effectSystem.successful {}

  private def send[R](
    sttpRequest: Request[R, WebSocket],
    requestId: String,
    protocol: Protocol,
  ): Effect[Response[R]] = {
    // Log the request
    lazy val requestProperties = ListMap(
      LogProperties.requestId -> requestId,
      "URL" -> sttpRequest.uri.toString
    ) ++ Option.when(protocol == Protocol.Http)("Method" -> sttpRequest.method.toString)
    log.sendingRequest(requestProperties, protocol.name)

    // Send the request
    val response = sttpRequest.send(backend.asInstanceOf[SttpBackend[Effect, WebSockets]])
    response.either.flatMap(
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

  private def createRequest(
    requestBody: Array[Byte],
    mediaType: String,
    requestContext: Context,
  ): Request[Array[Byte], WebSocket] = {
    // URL & method
    val transportRequest = requestContext.transportContext.map(_.request).getOrElse(basicRequest)
    val requestUrl = Uri(requestContext.overrideUrl(defaultUrl))
    val requestMethod = Method.unsafeApply(requestContext.method.getOrElse(method).name)

    // Headers, timeout & follow redirects
    val contentType = MediaType.unsafeParse(mediaType)
    val sttpRequest = transportRequest.method(requestMethod, requestUrl).headers(requestContext.headers.map {
      case (name, value) => Header(name, value)
    }*).contentType(contentType).header(Header.accept(contentType))
      .readTimeout(requestContext.timeout.getOrElse(transportRequest.options.readTimeout))
      .followRedirects(requestContext.followRedirects.getOrElse(transportRequest.options.followRedirects))
      .maxRedirects(transportRequest.options.maxRedirects)

    // Body & response type
    requestUrl.toString.toLowerCase match {
      case scheme if scheme.startsWith(webSocketsSchemePrefix) =>
        // Create WebSocket request
        sttpRequest.response(asWebSocketAlways(sendWebSocket(requestBody)))
      case _ =>
        // Create HTTP request
        sttpRequest.body(requestBody).response(asByteArrayAlways)
    }
  }

  private def sendWebSocket(request: Array[Byte]): sttp.ws.WebSocket[Effect] => Effect[Array[Byte]] =
    webSocket => webSocket.sendBinary(request).flatMap(_ => webSocket.receiveBinary(true))

  private def getResponseContext(response: Response[Array[Byte]]): Context =
    context.statusCode(response.code.code).headers(response.headers.map { header =>
      header.name -> header.value
    }*)

  private def transportProtocol(sttpRequest: Request[Array[Byte], WebSocket]): Effect[Protocol] =
    if (sttpRequest.isWebSocket) {
      if (webSocket) {
        effectSystem.successful(Protocol.WebSocket)
      } else {
        effectSystem.failed(
          throw new IllegalArgumentException(
            s"Selected STTP backend does not support WebSocket: ${backend.getClass.getSimpleName}"
          )
        )
      }
    } else {
      effectSystem.successful(Protocol.Http)
    }
}

case object SttpClient {

  /** Request context type. */
  type Context = HttpContext[TransportContext]

  /**
   * Creates an STTP HTTP & WebSocket client message transport plugin with the specified STTP backend.
   *
   * @param effectSystem
   *   effect system plugin
   * @param backend
   *   STTP backend
   * @param url
   *   HTTP or WebSocket server endpoint URL
   * @param method
   *   HTTP request method (default: POST)
   * @tparam Effect
   *   effect type
   * @return
   *   STTP HTTP & WebSocket client message transport plugin
   */
  def apply[Effect[_]](
    effectSystem: EffectSystem[Effect],
    backend: SttpBackend[Effect, WebSockets],
    url: URI,
    method: HttpMethod = HttpMethod.Post,
  ): SttpClient[Effect] =
    SttpClient[Effect](effectSystem, backend, url, method, webSocket = true)

  /**
   * Creates an STTP HTTP client message transport plugin with the specified STTP backend.
   *
   * @param effectSystem
   *   effect system plugin
   * @param backend
   *   STTP backend
   * @param url
   *   HTTP or WebSocket server endpoint URL
   * @param method
   *   HTTP request method (default: POST)
   * @tparam Effect
   *   effect type
   * @return
   *   STTP HTTP client message transport plugin
   */
  def http[Effect[_]](
    effectSystem: EffectSystem[Effect],
    backend: SttpBackend[Effect, ?],
    url: URI,
    method: HttpMethod = HttpMethod.Post,
  ): SttpClient[Effect] =
    SttpClient[Effect](effectSystem, backend, url, method, webSocket = false)

  /** Transport context. */
  final case class TransportContext(request: PartialRequest[Either[String, String], Any])

  case object TransportContext {

    /** Implicit default context value. */
    implicit val defaultContext: HttpContext[TransportContext] = HttpContext()
  }
}
