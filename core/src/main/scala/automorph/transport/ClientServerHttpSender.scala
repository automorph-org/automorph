package automorph.transport

import automorph.log.MessageLog.messageText
import automorph.log.{Logging, MessageLog}
import automorph.spi.EffectSystem
import automorph.util.Extensions.EffectOps
import java.net.URI
import java.util.concurrent.atomic.AtomicBoolean
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
 * @param httpListen
 *   listen for server requests settings
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
  method: HttpMethod,
  httpListen: HttpListen,
  effectSystem: EffectSystem[Effect],
) extends Logging {
  private val listening = new AtomicBoolean(false)
  private val log = MessageLog(logger)
  implicit private val system: EffectSystem[Effect] = effectSystem

  def call(body: Array[Byte], context: Context, id: String, contentType: String): Effect[(Array[Byte], Context)] =
    Try(createRequest(body, context, contentType)).fold(
      system.failed,
      { case (request, requestContext, protocol) =>
        val requestMetadata = HttpMetadata(requestContext.contentType(contentType).asInstanceOf[Context], protocol, id)
        send(request, body, requestMetadata).flatFold(
          { error =>
            log.failedReceiveResponse(error, requestMetadata.properties, requestMetadata.protocol.name)
            effectSystem.failed(error)
          },
          { case (responseBody, responseContext) =>
            lazy val responseProperties =
              requestMetadata.properties ++ responseContext.statusCode.map(MessageLog.status -> _.toString)
            log.receivedResponse(
              responseProperties,
              messageText(responseBody, responseContext.contentType),
              requestMetadata.protocol.name,
            )
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
        send(request, body, requestMetadata).map(_ => ())
      },
    )

  def init(): Unit =
    if (httpListen.connections >= 0) {
      listening.set(true)
    }

  def close(): Unit =
    listening.set(false)

  private def send(
    request: Request,
    requestBody: Array[Byte],
    requestMetadata: HttpMetadata[Context],
  ): Effect[(Array[Byte], Context)] = {
    log.sendingRequest(
      requestMetadata.properties,
      messageText(requestBody, requestMetadata.context.contentType),
      requestMetadata.protocol.name,
    )
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
//
//  private def listen(): Effect[Unit] = {
//    system.runAsync()
//  }
}
