package automorph.transport.client

import automorph.log.Logging
import automorph.spi.{ClientTransport, EffectSystem}
import automorph.transport.HttpClientBase.{overrideUrl, webSocketSchemePrefix}
import automorph.transport.client.SttpClient.{Context, Transport}
import automorph.transport.{ClientServerHttpSender, HttpMethod, Protocol}
import automorph.util.Extensions.EffectOps
import sttp.capabilities.WebSockets
import sttp.client3.{Request, SttpBackend, asByteArrayAlways, asWebSocketAlways, basicRequest}
import sttp.model.{Header, MediaType, Method, Uri}
import java.net.URI

private[automorph] trait SttpClientBase[Effect[_]] extends ClientTransport[Effect, Context] with Logging {

  private type WebSocket = sttp.capabilities.Effect[Effect] & WebSockets
  private type GenericRequest = Request[Array[Byte], WebSocket]

  private val baseUrl = Uri(url).toJavaUri
  private val sender = ClientServerHttpSender(createRequest, sendRequest, url, method, effectSystem)
  implicit private val system: EffectSystem[Effect] = effectSystem

  def url: URI

  def backend: SttpBackend[Effect, ?]

  def method: HttpMethod

  def webSocketSupport: Boolean

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
    effectSystem.successful {}

  override def close(): Effect[Unit] =
    effectSystem.successful {}

  private def createRequest(
    requestBody: Array[Byte],
    context: Context,
    contentType: String,
  ): (GenericRequest, Context, Protocol) = {
    // URL & method
    val transportRequest = context.transportContext.map(_.request).getOrElse(basicRequest)
    val requestUrl = Uri(overrideUrl(baseUrl, context))
    val requestMethod = Method.unsafeApply(context.method.getOrElse(method).name)

    // Headers, timeout & follow redirects
    val contentTypeValue = MediaType.unsafeParse(contentType)
    val sttpRequest = transportRequest.method(requestMethod, requestUrl).headers(context.headers.map {
      case (name, value) => Header(name, value)
    }*).contentType(contentTypeValue).header(Header.accept(contentTypeValue))
      .readTimeout(context.timeout.getOrElse(transportRequest.options.readTimeout))
      .followRedirects(context.followRedirects.getOrElse(transportRequest.options.followRedirects))
      .maxRedirects(transportRequest.options.maxRedirects)

    // Body & response type
    requestUrl.toString.toLowerCase match {
      case scheme if scheme.startsWith(webSocketSchemePrefix) =>
        if (!webSocketSupport) {
          throw new UnsupportedOperationException(
            s"Selected STTP backend does not support WebSocket: ${backend.getClass.getSimpleName}"
          )
        }
        val request = sttpRequest.response(asWebSocketAlways[Effect, Array[Byte]] { webSocket =>
          webSocket.sendBinary(requestBody).flatMap(_ => webSocket.receiveBinary(true))
        })
        (request, context.url(request.uri.toJavaUri), Protocol.WebSocket)
     case _ =>
        val request = sttpRequest.body(requestBody).response(asByteArrayAlways)
        val requestContext = context.url(request.uri.toJavaUri).method(HttpMethod.valueOf(requestMethod.method))
        (request, requestContext, Protocol.Http)
    }
  }

  private def sendRequest(request: GenericRequest, requestContext: Context): Effect[(Array[Byte], Context)] =
    request.send(backend.asInstanceOf[SttpBackend[Effect, WebSockets]]).map { response =>
      val responseContext = if (request.isWebSocket) {
        requestContext.statusCode(response.code.code)
      } else {
        requestContext
      }.headers(response.headers.map { header =>
        header.name -> header.value
      }*)
      response.body -> responseContext
    }
}
