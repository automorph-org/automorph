package automorph.transport

import automorph.RpcException.{InvalidArguments, InvalidResponse}
import automorph.log.MessageLog.messageText
import automorph.log.{Logging, MessageLog}
import automorph.spi.EffectSystem.Completable
import automorph.spi.{EffectSystem, RpcHandler}
import automorph.transport.HttpContext.{headerRpcCallId, headerRpcListen}
import automorph.transport.ServerHttpHandler.{contentTypeText, valueRpcListen}
import automorph.util.Extensions.EffectOps
import automorph.util.Random
import java.util.concurrent.atomic.AtomicReference
import scala.collection.concurrent.TrieMap
import scala.collection.mutable

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
  receiveRequest: Request => (Effect[Array[Byte]], Context),
  sendResponse: (Array[Byte], HttpMetadata[Context], Connection) => Effect[Unit],
  protocol: Protocol,
  effectSystem: EffectSystem[Effect],
  mapException: Throwable => Int,
  rpcHandler: RpcHandler[Effect, Context],
  getRpcCallId: Option[Connection => Option[String]] = None,
  setRpcCallId: Option[(Connection, Option[String]) => Unit] = None,
  requestRetries: Int = 1,
) extends Logging {
  private val log = MessageLog(logger, protocol.name)
  private val handler =
    ServerHttpHandler(receiveRequest, sendResponse, protocol, effectSystem, mapException, rpcHandler)
  private val listenConnections =
    ConnectionPool[Effect, Unit, Connection](
      None,
      _ => system.successful {},
      protocol,
      effectSystem,
      None,
    )
  private val expectedResponses =
    TrieMap.empty[String, mutable.Map[String, Completable[Effect, (Array[Byte], Context)]]].withDefault(TrieMap.empty)
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
  def processRequest(request: Request, connection: Connection): Effect[Unit] =
    handler.retrieveRequest(request).flatMap { case (requestBody, requestMetadata) =>
      val context = requestMetadata.context
      getRpcCallId.flatMap(_(connection)).orElse(context.header(headerRpcCallId))
        .flatMap(callId => context.peer.map(_ -> callId)).map { case (peer, callId) =>
          // Return the received response to the caller
          processRpcResponse(peer, callId, requestBody, requestMetadata, connection)
        }.getOrElse {
          context.header(headerRpcListen).filter(_.toLowerCase == valueRpcListen).flatMap(_ => context.peer).map {
            peer =>
              // Accept listen connection
              log.acceptedListenConnection(requestMetadata.properties, requestMetadata.protocol.name)
              listenConnections.add(peer, connection)
          }.getOrElse {
            // Process the request
            handler.handleRequest(requestBody, requestMetadata, connection)
          }
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
    context.peer.map { peer =>
      effectSystem.completable[(Array[Byte], Context)].flatMap { expectedResponse =>
        val callId = Random.id
        expectedResponses(peer).put(callId, expectedResponse)
        send(body, context, peer, Some(callId)).flatMap(_ => expectedResponse.effect)
      }
    }.getOrElse {
      system.failed(InvalidArguments("Peer identifier not found in the call context"))
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
      send(body, context, peer, None)
    }.getOrElse {
      system.failed(InvalidArguments("Peer identifier not found in the call context"))
    }

  private def send(body: Array[Byte], context: Context, peer: String, rpcCallId: Option[String]): Effect[Unit] = {
    val contentType = handler.rpcHandler.mediaType
    val metadata = HttpMetadata(context, protocol, contentType)
    system.retry(
      listenConnections.acquire(peer, ()).flatMap { case (connection, _) =>
        setRpcCallId.foreach(_(connection, rpcCallId))
        handler.respond(body, contentType, context.statusCode, Some(context), metadata, connection)
      },
      requestRetries,
    )
  }

  @scala.annotation.nowarn
  private def processRpcResponse(
    peer: String,
    callId: String,
    body: Array[Byte],
    metadata: HttpMetadata[Context],
    connection: Connection,
  ): Effect[Unit] = {
    log.receivedResponse(metadata.properties, messageText(body, metadata.context.contentType), metadata.protocol.name)
    expectedResponses(peer).remove(callId).map { expectedResponse =>
      expectedResponse.succeed(body -> metadata.context)
      if (protocol == Protocol.Http) {
        handler.respond(Array.emptyByteArray, contentTypeText, None, None, metadata, connection)
      } else {
        system.successful {}
      }
    }.getOrElse {
      val error = InvalidResponse(s"Invalid call identifier: $callId")
      handler.respondError(error, connection, metadata)
    }
  }
}

private[automorph] object ClientServerHttpHandler {

  type RpcCallId = AtomicReference[Option[String]]
}