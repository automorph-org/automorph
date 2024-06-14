package automorph.transport.websocket.endpoint

import automorph.spi.{EffectSystem, RpcHandler, ServerTransport}
import automorph.transport.ServerHttpHandler.HttpMetadata
import automorph.transport.server.VertxHttpEndpoint
import automorph.transport.websocket.endpoint.VertxWebSocketEndpoint.Context
import automorph.transport.{ClientServerHttpHandler, HttpContext, Protocol}
import automorph.util.Extensions.{EffectOps, StringOps}
import io.vertx.core.Handler
import io.vertx.core.buffer.Buffer
import io.vertx.core.http.{HttpServerRequest, ServerWebSocket}
import scala.annotation.unused
import scala.jdk.CollectionConverters.ListHasAsScala

/**
 * Vert.x WebSocket endpoint message transport plugin.
 *
 * Interprets WebSocket request message as an RPC request and processes it using the specified RPC request handler.
 *   - The response returned by the RPC request handler is used as WebSocket response message.
 *
 * @see
 *   [[https://en.wikipedia.org/wiki/WebSocket Transport protocol]]
 * @see
 *   [[https://vertx.io Library documentation]]
 * @see
 *   [[https://vertx.io/docs/apidocs/index.html API]]
 * @constructor
 *   Creates a Vert.x Websocket endpoint message transport plugin with specified effect system and request handler.
 * @param effectSystem
 *   effect system plugin
 * @param handler
 *   RPC request handler
 * @tparam Effect
 *   effect type
 */
final case class VertxWebSocketEndpoint[Effect[_]](
  effectSystem: EffectSystem[Effect],
  handler: RpcHandler[Effect, Context] = RpcHandler.dummy[Effect, Context],
) extends ServerTransport[Effect, Context, Handler[ServerWebSocket]] {

  private lazy val requestHandler = new Handler[ServerWebSocket] {

    override def handle(session: ServerWebSocket): Unit = {
      session.binaryMessageHandler { buffer =>
        webSocketHandler.processRequest((buffer.getBytes, session), session).runAsync
      }
      session.textMessageHandler { message =>
        webSocketHandler.processRequest((message.toByteArray, session), session).runAsync
      }
      session.exceptionHandler { error =>
        webSocketHandler.failedReceiveWebSocketRequest(error)
      }
      ()
    }
  }

  private val webSocketHandler =
    ClientServerHttpHandler(receiveRequest, sendResponse, Protocol.WebSocket, effectSystem, _ => 0, handler)
  implicit private val system: EffectSystem[Effect] = effectSystem

  override def adapter: Handler[ServerWebSocket] =
    requestHandler

  override def init(): Effect[Unit] =
    effectSystem.successful {}

  override def close(): Effect[Unit] =
    effectSystem.successful {}

  override def requestHandler(handler: RpcHandler[Effect, Context]): VertxWebSocketEndpoint[Effect] =
    copy(handler = handler)

  private def receiveRequest(
    incomingRequest: (Array[Byte], ServerWebSocket)
  ): (Effect[Array[Byte]], HttpMetadata[Context]) = {
    val (body, webSocket) = incomingRequest
    val requestMetadata = HttpMetadata(
      getRequestContext(webSocket),
      webSocketHandler.protocol,
      webSocket.uri,
      None,
    )
    system.successful(body) -> requestMetadata
  }

  private def sendResponse(
    body: Array[Byte],
    @unused metadata: HttpMetadata[Context],
    session: ServerWebSocket,
  ): Effect[Unit] =
    effectSystem.completable[Unit].flatMap { completable =>
      session.writeBinaryMessage(Buffer.buffer(body))
        .onSuccess(_ => completable.succeed(()).runAsync)
        .onFailure(error => completable.fail(error).runAsync)
      completable.effect
    }

  private def getRequestContext(webSocket: ServerWebSocket): Context = {
    val headers = webSocket.headers.entries.asScala.map(entry => entry.getKey -> entry.getValue).toSeq
    HttpContext(
      transportContext = Some(Right(webSocket).withLeft[HttpServerRequest]),
      headers = headers,
      peer = Some(client(webSocket)),
    ).url(
      webSocket.uri
    )
  }

  private def client(webSocket: ServerWebSocket): String =
    VertxHttpEndpoint.clientId(webSocket.headers, webSocket.remoteAddress)
}

object VertxWebSocketEndpoint {

  /** Request context type. */
  type Context = HttpContext[Either[HttpServerRequest, ServerWebSocket]]
}
