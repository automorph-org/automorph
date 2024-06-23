package automorph.transport

import automorph.log.{LogProperties, Logging, MessageLog}
import automorph.spi.EffectSystem
import automorph.util.Extensions.EffectOps
import java.net.URI
import scala.util.Try

/**
 * HTTP or WebSocket RPC request handler.
 *
 * @constructor
 *   Creates a HTTP or WebSocket RPC request send.
 * @param createRequest
 *   function to determine the transport context URL
 * @param sendRequest
 *   function to send a HTTP/WebSocket request
 * @param effectSystem
 *   effect system plugin
 * @tparam Effect
 *   effect type
 * @tparam Context
 *   RPC message context type
 * @tparam Request
 *   HTTP/WebSocket request type
 */
final private[automorph] case class ClientServerHttpSender[Effect[_], Context <: HttpContext[?], Request](
  createRequest: (Array[Byte], Context, String) => (Request, Context, Protocol),
  sendRequest: (Request, Context) => Effect[(Array[Byte], Context)],
  url: URI,
  method: HttpMethod = HttpMethod.Post,
  effectSystem: EffectSystem[Effect],
) extends Logging {
  private val log = MessageLog(logger, Protocol.Http.name)
  implicit private val system: EffectSystem[Effect] = effectSystem

  def call(body: Array[Byte], context: Context, id: String, contentType: String): Effect[(Array[Byte], Context)] =
    Try(createRequest(body, context, contentType)).fold(
      system.failed,
      { case (request, requestContext, protocol) =>
        val requestMetadata = HttpMetadata(requestContext, protocol, id)
        send(request, requestMetadata).flatFold(
          { error =>
            log.failedReceiveResponse(error, requestMetadata.properties, requestMetadata.protocol.name)
            effectSystem.failed(error)
          },
          { case (responseBody, responseContext) =>
            lazy val responseProperties =
              requestMetadata.properties ++ responseContext.statusCode.map(LogProperties.status -> _.toString)
            log.receivedResponse(responseProperties, requestMetadata.protocol.name)
            effectSystem.successful(responseBody -> responseContext)
          },
        )
      },
    )

  def tell(body: Array[Byte], context: Context, id: String, contentType: String): Effect[Unit] =
    Try(createRequest(body, context, contentType)).fold(
      system.failed,
      { case (request, requestContext, protocol) =>
        val requestMetadata = HttpMetadata(requestContext, protocol, id)
        send(request, requestMetadata).map(_ => ())
      },
    )

  def listen(connections: Int): Unit =
    if (connections >= 0) {
      ()
    }

  private def send(request: Request, requestMetadata: HttpMetadata[Context]): Effect[(Array[Byte], Context)] = {
    log.sendingRequest(requestMetadata.properties, requestMetadata.protocol.name)
    sendRequest(request, requestMetadata.context).flatFold(
      { error =>
        log.failedSendRequest(error, requestMetadata.properties, requestMetadata.protocol.name)
        effectSystem.failed(error)
      },
      { response =>
        log.sentRequest(requestMetadata.properties, requestMetadata.protocol.name)
        effectSystem.successful(response)
      },
    )
  }
}
