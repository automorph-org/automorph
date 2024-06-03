package automorph.transport

import automorph.log.{Logging, MessageLog}
import automorph.spi.EffectSystem.Completable
import automorph.spi.{EffectSystem, RequestHandler}
import automorph.transport.HttpContext.{headerRpcCallId, headerRpcListen}
import automorph.transport.HttpRequestHandler.{RequestMetadata, ResponseMetadata, valueRpcListen}
import automorph.util.Extensions.EffectOps
import automorph.util.Random
import scala.collection.concurrent.TrieMap

/**
 * Callback-based HTTP or WebSocket RPC request handler.
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
final private[automorph] case class CallbackHttpRequestHandler[
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
  retainFaultyConnections: Boolean = false,
  requestRetries: Int = 1,
) extends Logging {
  private val log = MessageLog(logger, protocol.name)
  private val handler =
    HttpRequestHandler(receiveRequest, sendResponse, protocol, effectSystem, mapException, requestHandler)
  private val connectionPool =
    ConnectionPool[Effect, Unit, Connection](
      None,
      _ => system.successful {},
      protocol,
      effectSystem,
      None,
      retain = retainFaultyConnections,
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
    handler.receiveRpcRequest(request).flatMap { case (requestBody, requestMetadata) =>
      if (requestMetadata.context.header(headerRpcListen).exists(_.toLowerCase == valueRpcListen)) {
        // Register client connection
        log.receivedConnection(requestMetadata.properties, requestMetadata.protocol.name)
        connectionPool.add(requestMetadata.client, connection)
      } else {
        requestMetadata.context.header(headerRpcCallId).map { callId =>
          // Return the received response
          processResponse(callId, requestBody, requestMetadata, connection)
        }.getOrElse {
          // Process the request
          handler.processRequest(requestBody, requestMetadata, connection)
        }
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
      val statusCode = context.statusCode.getOrElse(handler.statusOk)
      val contentType = handler.requestHandler.mediaType
      val requestMetadata = RequestMetadata(context, protocol, "", None, peerId)
      system.retry(
        connectionPool.using(
          peerId,
          (),
          connection =>
            handler.sendRpcResponse(body, contentType, statusCode, Some(context), connection, requestMetadata),
        ),
        requestRetries,
      )
    }.getOrElse {
      system.failed(new IllegalArgumentException("Peer identifier not found in call context"))
    }

  private def processResponse(
    callId: String,
    requestBody: Array[Byte],
    requestMetadata: RequestMetadata[Context],
    connection: Connection,
  ): Effect[Unit] = {
    log.receivedResponse(requestMetadata.properties, requestMetadata.protocol.name)
    expectedResponses.get(callId).map { expectedResponse =>
      expectedResponses.remove(callId)
      expectedResponse.succeed(requestBody -> requestMetadata.context)
      val contentType = handler.requestHandler.mediaType
      handler.sendRpcResponse(Array.empty, contentType, handler.statusOk, None, connection, requestMetadata).flatFold(
        { error =>
          // FIXME - implement faulty connection removal
//          if (!retainConnections) connectionPool.remove(null, null)
          system.failed[Unit](error)
        },
        system.successful,
      )
    }.getOrElse {
      val error = new IllegalStateException(s"Invalid call identifier: $callId")
      handler.sendErrorResponse(error, connection, requestMetadata)
    }
  }
}
