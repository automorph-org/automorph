package automorph.transport

import automorph.log.MessageLog.messageText
import automorph.log.{Logging, MessageLog}
import automorph.spi.EffectSystem
import automorph.transport.HttpContext.{headerRpcListen, headerRpcNodeId}
import automorph.transport.ServerHttpHandler.{contentTypeText, valueRpcListen}
import automorph.util.Extensions.EffectOps
import automorph.util.Random
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
  nodeId: Option[String],
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
        sendReceive(request, body, requestMetadata)
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
      Range(0, httpListen.connections).foreach(_ => system.runAsync(listen()))
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

  private def sendReceive(
    request: Request,
    requestBody: Array[Byte],
    requestMetadata: HttpMetadata[Context],
  ): Effect[(Array[Byte], Context)] =
    send(request, requestBody, requestMetadata).flatFold(
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

  private def listen(): Effect[Unit] =
    if (listening.get) {
        system.evaluate {
          val requestContext = nodeId.map(HttpContext().header(headerRpcNodeId, _)).getOrElse(HttpContext())
            .header(headerRpcListen, valueRpcListen).asInstanceOf[Context]
          createRequest(Array.emptyByteArray, requestContext, contentTypeText)
        }.flatMap { case (request, context, protocol) =>
            val requestMetadata =
              HttpMetadata(context.contentType(contentTypeText).asInstanceOf[Context], protocol, Random.id)
            sendReceive(request, Array.emptyByteArray, requestMetadata).fold(
              { _ =>
                // Retry the listen request
                system.runAsync(system.sleep(httpListen.retryInterval).flatMap(_ => listen()))
              },
              { case (responseBody, responseContext) =>
                // Process the request
                system.runAsync(listen())
                handleRequest(responseBody, requestMetadata.copy(context = responseContext))
              },
            )
          }
    } else {
      effectSystem.successful {}
    }

  private def handleRequest(body: Array[Byte], metadata: HttpMetadata[Context]): Effect[Unit] = {
    // FIXME - implement

  }
}
