package automorph.transport.client

import automorph.log.{LogProperties, Logging, MessageLog}
import automorph.spi.{ClientTransport, EffectSystem}
import automorph.transport.HttpClientBase.overrideUrl
import automorph.transport.client.SttpClient.{Context, Transport}
import automorph.transport.{HttpMethod, Protocol}
import automorph.util.Extensions.EffectOps
import sttp.capabilities.WebSockets
import sttp.client3.{Request, Response, SttpBackend, asByteArrayAlways, asWebSocketAlways, basicRequest, ignore}
import sttp.model.{Header, MediaType, Method, Uri}
import java.net.URI
import scala.collection.immutable.ListMap

private[automorph] trait SttpClientBase[Effect[_]] extends ClientTransport[Effect, Context] with Logging {

  private type WebSocket = sttp.capabilities.Effect[Effect] & WebSockets

  private val webSocketsSchemePrefix = "ws"
  private val baseUrl = Uri(url).toJavaUri
  private val log = MessageLog(logger, Protocol.Http.name)
  implicit private val system: EffectSystem[Effect] = effectSystem

  def url: URI

  def backend: SttpBackend[Effect, ?]

  def method: HttpMethod

  def webSocket: Boolean

  override def call(
    requestBody: Array[Byte],
    requestContext: Context,
    requestId: String,
    mediaType: String,
  ): Effect[(Array[Byte], Context)] = {
    // Create the request
    val sttpRequest = createRequest(requestBody, mediaType, requestContext)
    lazy val responseProperties = ListMap(
      LogProperties.requestId -> requestId,
      LogProperties.url -> sttpRequest.uri.toString,
    )

    // Send the request and process the response
    transportProtocol(sttpRequest).flatMap { protocol =>
      send(sttpRequest, requestId, protocol).flatFold(
        error => {
          log.failedReceiveResponse(error, responseProperties, protocol.name)
          effectSystem.failed(error)
        },
        response => {
          log.receivedResponse(responseProperties + (LogProperties.status -> response.code.toString), protocol.name)
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
  ): Effect[Unit] = {
    val sttpRequest = createRequest(requestBody, mediaType, requestContext)
    transportProtocol(sttpRequest).flatMap {
      case Protocol.Http => send(sttpRequest.response(ignore), requestId, Protocol.Http).map(_ => ())
      case Protocol.WebSocket => send(sttpRequest, requestId, Protocol.WebSocket).map(_ => ())
    }
  }

  override def context: Context =
    Transport.context.url(url).method(method)

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
      LogProperties.url -> sttpRequest.uri.toString,
    ) ++ Option.when(protocol == Protocol.Http)(LogProperties.method -> sttpRequest.method.toString)
    log.sendingRequest(requestProperties, protocol.name)

    // Send the request
    val response = sttpRequest.send(backend.asInstanceOf[SttpBackend[Effect, WebSockets]])
    response.flatFold(
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

  private def createRequest(
    requestBody: Array[Byte],
    mediaType: String,
    requestContext: Context,
  ): Request[Array[Byte], WebSocket] = {
    // URL & method
    val transportRequest = requestContext.transportContext.map(_.request).getOrElse(basicRequest)
    val requestUrl = Uri(overrideUrl(baseUrl, requestContext))
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
        sttpRequest.response(asWebSocketAlways { webSocket =>
          webSocket.sendBinary(requestBody).flatMap(_ => webSocket.receiveBinary(true))
        })
      case _ =>
        // Create HTTP request
        sttpRequest.body(requestBody).response(asByteArrayAlways)
    }
  }

  private def getResponseContext(response: Response[Array[Byte]]): Context =
    context.statusCode(response.code.code).headers(response.headers.map { header =>
      header.name -> header.value
    }*)

  private def transportProtocol(sttpRequest: Request[Array[Byte], WebSocket]): Effect[Protocol] =
    if (sttpRequest.isWebSocket) {
      if (webSocket) {
        effectSystem.successful(Protocol.WebSocket)
      } else {
        effectSystem.failed(new IllegalArgumentException(
          s"Selected STTP backend does not support WebSocket: ${backend.getClass.getSimpleName}"
        ))
      }
    } else {
      effectSystem.successful(Protocol.Http)
    }
}
