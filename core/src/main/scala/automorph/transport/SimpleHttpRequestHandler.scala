package automorph.transport

import automorph.log.Logging
import automorph.spi.{EffectSystem, RequestHandler}
import automorph.transport.HttpRequestHandler.{RequestMetadata, ResponseMetadata}
import automorph.util.Extensions.EffectOps

/**
 * Simple HTTP or WebSocket RPC request handler.
 *
 * @constructor
 *   Creates a HTTP or WebSocket RPC request handler.
 * @param receiveRequest
 *   function to receive a HTTP/WebSocket request
 * @param createResponse
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
 * @tparam Response
 *   HTTP/WebSocket response type
 * @tparam Connection
 *   HTTP/WebSocket connection type
 */
final private[automorph] case class SimpleHttpRequestHandler[
  Effect[_],
  Context <: HttpContext[?],
  Request,
  Response,
  Connection,
](
  receiveRequest: Request => (RequestMetadata[Context], Effect[Array[Byte]]),
  createResponse: (ResponseMetadata[Context], Connection) => Effect[Response],
  protocol: Protocol,
  effectSystem: EffectSystem[Effect],
  mapException: Throwable => Int,
  requestHandler: RequestHandler[Effect, Context],
) extends Logging {
  private val handler =
    HttpRequestHandler(receiveRequest, createResponse, protocol, effectSystem, mapException, requestHandler)
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
  def processRequest(request: Request, connection: Connection): Effect[Response] =
    // Receive the request
    handler.receiveRpcRequest(request).flatMap { case (requestBody, requestMetadata) =>
      // Process the request
      handler.processRequest(requestBody, requestMetadata, connection)
    }
}
