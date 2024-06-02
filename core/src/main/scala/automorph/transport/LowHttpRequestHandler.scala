package automorph.transport

import automorph.log.Logging
import automorph.spi.{EffectSystem, RequestHandler}
import automorph.transport.HttpContext.headerRpcListen
import automorph.transport.HttpRequestHandler.{RequestMetadata, ResponseData, valueRpcListen}
import automorph.util.Extensions.EffectOps

/**
 * Low-level HTTP or WebSocket RPC request handler.
 *
 * @constructor
 *   Creates a HTTP or WebSocket RPC request handler.
 * @param receiveRequest
 *   function to receive a HTTP/WebSocket request
 * @param sendResponse
 *   function to send a HTTP/WebSocket response
 * @param protocol
 *   transport protocol
 * @param effectSystem
 *   effect system plugin
 * @param mapException
 *   maps an exception to a corresponding HTTP status code
 * @param requestHandler
 *   RPC request handler
 * @tparam Effect
 *   effect type
 * @tparam Context
 *   RPC message context type
 * @tparam Request
 *   HTTP/WebSocket request type
 * @tparam Connection
 *   HTTP/WebSocket connection type
 */
final private[automorph] case class LowHttpRequestHandler[
  Effect[_],
  Context <: HttpContext[?],
  Request,
  Connection,
](
  receiveRequest: Request => (RequestMetadata[Context], Effect[Array[Byte]]),
  sendResponse: (ResponseData[Context], Connection) => Effect[Unit],
  protocol: Protocol,
  effectSystem: EffectSystem[Effect],
  mapException: Throwable => Int,
  requestHandler: RequestHandler[Effect, Context],
  requestRetries: Int = 1,
) extends Logging {
  private val handler =
    HttpRequestHandler(receiveRequest, sendResponse, protocol, effectSystem, mapException, requestHandler)
  private val responsePool = {
    val closeConnection = (_: Connection) => system.successful {}
    ConnectionPool[Effect, String, Connection](None, closeConnection, protocol, effectSystem, None, retain = false)
  }
  implicit private val system: EffectSystem[Effect] = effectSystem

  /**
   * Process HTTP or WebSocket RPC request.
   *
   * @param request
   *   HTTP or WebSocket RPC request
   * @param connection
   *   HTTP or WebSocket connection
   * @return
   *   HTTP or WebSocket response
   */
  def processRequest(request: Request, connection: Connection): Effect[Unit] =
    handler.receiveRpcRequest(request).flatMap { case (requestMetadata, requestBody) =>
      if (requestMetadata.context.header(headerRpcListen).exists(_.toLowerCase == valueRpcListen)) {
        // Register long polling connection
        responsePool.add(requestMetadata.client, connection)
      } else {
        // Process the request
        handler.processRequest(requestBody, requestMetadata, connection)
      }
    }
}
