package automorph.transport

import automorph.log.Logging
import automorph.spi.EffectSystem.Completable
import automorph.spi.{EffectSystem, RequestHandler}
import automorph.transport.HttpContext.{headerRpcCallId, headerRpcListen}
import automorph.transport.HttpRequestHandler.{RequestMetadata, ResponseMetadata, valueRpcListen}
import automorph.util.Extensions.EffectOps
import automorph.util.Random
import scala.collection.concurrent.TrieMap

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
  sendResponse: (ResponseMetadata[Context], Connection) => Effect[Unit],
  protocol: Protocol,
  effectSystem: EffectSystem[Effect],
  mapException: Throwable => Int,
  requestHandler: RequestHandler[Effect, Context],
  retainConnections: Boolean = false,
  requestRetries: Int = 1,
) extends Logging {
  private val handler =
    HttpRequestHandler(receiveRequest, sendResponse, protocol, effectSystem, mapException, requestHandler)
  private val connectionPool =
    ConnectionPool[Effect, Unit, Connection](
      None,
      _ => system.successful {},
      protocol,
      effectSystem,
      None,
      retain = retainConnections,
    )
  private val expectedResponses = TrieMap[String, Completable[Effect, (Array[Byte], Context)]]()
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
        connectionPool.add(requestMetadata.client, connection)
      } else {
        // Process the request
        handler.processRequest(requestBody, requestMetadata, connection)
      }
    }

  /**
   * Sends a request to a remote endpoint and retrieves the response.
   *
   * An optional request context is used to supply additional information needed to send the request.
   *
   * @param body
   *   request message body
   * @param context
   *   request context
   * @return
   *   response message and context
   */
  def call(body: Array[Byte], context: Context): Effect[(Array[Byte], Context)] =
    effectSystem.completable[(Array[Byte], Context)].flatMap { expectedResponse =>
      val callId = Random.id
      val rpcCallContext = context.header(headerRpcCallId, callId).asInstanceOf[Context]
      expectedResponses.put(callId, expectedResponse)
      tell(body, rpcCallContext).flatMap(_ => expectedResponse.effect)
    }

  /**
   * Sends a request to a remote endpoint without waiting for a response.
   *
   * An optional request context is used to supply additional information needed to send the request.
   *
   * @param body
   *   request message body
   * @param context
   *   request context
   * @return
   *   nothing
   */
  def tell(body: Array[Byte], context: Context): Effect[Unit] =
    context.peerId.map { peerId =>
      // FIXME - implement retries
      val statusCode = context.statusCode.getOrElse(handler.statusOk)
      val contentType = handler.requestHandler.mediaType
      val requestMetadata = RequestMetadata(context, protocol, "", None, peerId)
      connectionPool.using(
        peerId,
        (),
        connection =>
          handler.sendRpcResponse(body, contentType, statusCode, Some(context), connection, requestMetadata),
      )
//      val sendResponseAttempts = LazyList.iterate(
//        system.successful[Option[Throwable]](None) -> requestRetries
//      ) { case (attempt, retries) =>
//        attempt.flatMap(_ =>
//          connectionPool.using(
//            peerId,
//            (),
//            connection =>
//              handler.sendRpcResponse(body, contentType, statusCode, Some(context), connection, requestMetadata).fold(
//                error => Some(error),
//                _ => None,
//              ),
//          )
//        )
//      }
//      sendResponseAttempts.tail.dropWhile()
    }.getOrElse {
      system.failed(new IllegalArgumentException("Peer identifier not found in call context"))
    }
}
