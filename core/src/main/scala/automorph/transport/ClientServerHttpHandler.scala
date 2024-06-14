package automorph.transport

import automorph.RpcException.{InvalidArguments, InvalidResponse}
import automorph.log.{Logging, MessageLog}
import automorph.spi.EffectSystem.Completable
import automorph.spi.{EffectSystem, RpcHandler}
import automorph.transport.HttpContext.{headerRpcCallId, headerRpcListen}
import automorph.transport.ServerHttpHandler.{HttpMetadata, valueRpcListen}
import automorph.util.Extensions.EffectOps
import automorph.util.Random
import scala.collection.concurrent.TrieMap

/**
 * HTTP or WebSocket RPC request handler with client transport support.
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
 * @param rpcHandler
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
final private[automorph] case class ClientServerHttpHandler[
  Effect[_],
  Context <: HttpContext[?],
  Request,
  Connection,
](
  receiveRequest: Request => (Effect[Array[Byte]], HttpMetadata[Context]),
  sendResponse: (Array[Byte], HttpMetadata[Context], Connection) => Effect[Unit],
  protocol: Protocol,
  effectSystem: EffectSystem[Effect],
  mapException: Throwable => Int,
  rpcHandler: RpcHandler[Effect, Context],
  requestRetries: Int = 1,
) extends Logging {
  private val log = MessageLog(logger, protocol.name)
  private val handler =
    ServerHttpHandler(receiveRequest, sendResponse, protocol, effectSystem, mapException, rpcHandler)
  private val connectionPool =
    ConnectionPool[Effect, Unit, Connection](
      None,
      _ => system.successful {},
      protocol,
      effectSystem,
      None,
      retain = protocol == Protocol.WebSocket,
    )
  private val expectedResponses = TrieMap[String, Completable[Effect, (Array[Byte], Context)]]()
  implicit private val system: EffectSystem[Effect] = effectSystem

  /**
   * Processes an HTTP or WebSocket RPC request.
   *
   * @param request
   *   request
   * @param connection
   *   HTTP or WebSocket connection
   * @return
   *   response
   */
  def processRequest(request: Request, connection: Connection): Effect[Unit] = {
    handler.retrieveRequest(request).flatMap { case (requestBody, requestMetadata) =>
      handler.handleRequest(requestBody, requestMetadata, connection)
//      val context = requestMetadata.context
//      context.header(headerRpcListen).filter(_.toLowerCase == valueRpcListen).flatMap(_ => context.peer).map { peer =>
//        // Register client connection
//        log.receivedConnection(requestMetadata.properties, requestMetadata.protocol.name)
//        connectionPool.add(peer, connection)
//        handler.handleRequest(requestBody, requestMetadata, connection)
//      }.getOrElse {
//        context.header(headerRpcCallId).map { callId =>
//          // Return the received response
//          handler.handleRequest(requestBody, requestMetadata, connection)
//          processRpcResponse(callId, requestBody, requestMetadata, connection)
//        }.getOrElse {
//          // Process the request
//          handler.handleRequest(requestBody, requestMetadata, connection)
//        }
//      }
    }
  }

  def failedReceiveWebSocketRequest(error: Throwable): Unit =
    log.failedReceiveRequest(error, Map.empty, Protocol.WebSocket.name)

  /**
   * Sends a request to a remote endpoint and retrieves the response.
   *
   * Request context is used to supply additional information needed to send the desired request.
   *
   * @param body
   *   request body
   * @param context
   *   request context
   * @return
   *   response body and context
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
   * Request context is used to supply additional information needed to send the desired request.
   *
   * @param body
   *   request body
   * @param context
   *   request context
   * @return
   *   nothing
   */
  def tell(body: Array[Byte], context: Context): Effect[Unit] =
    context.peer.map { peer =>
      val contentType = handler.rpcHandler.mediaType
      val statusCode = context.statusCode
      val metadata = HttpMetadata(context, protocol, None, None, contentType, context.statusCode)
      system.retry(
        connectionPool.using(peer, (), handler.respond(body, contentType, statusCode, Some(context), metadata, _)),
        requestRetries,
      )
    }.getOrElse {
      system.failed(InvalidArguments("Peer identifier not found in the call context"))
    }

  @scala.annotation.nowarn
  private def processRpcResponse(
    callId: String,
    body: Array[Byte],
    metadata: HttpMetadata[Context],
    connection: Connection,
  ): Effect[Unit] = {
    log.receivedResponse(metadata.properties, metadata.protocol.name)
    expectedResponses.get(callId).map { expectedResponse =>
      expectedResponses.remove(callId)
      expectedResponse.succeed(body -> metadata.context)
      val contentType = handler.rpcHandler.mediaType
      if (protocol == Protocol.Http) {
        handler.respond(Array.emptyByteArray, contentType, None, None, metadata, connection)
      } else {
        system.successful {}
      }
    }.getOrElse {
      val error = InvalidResponse(s"Invalid call identifier: $callId")
      handler.respondError(error, connection, metadata)
    }
  }
}
