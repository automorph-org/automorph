package automorph.transport.websocket.endpoint

import automorph.spi.{EffectSystem, RequestHandler, ServerTransport}
import automorph.transport.HttpRequestHandler.{RequestMetadata, ResponseMetadata}
import automorph.transport.server.VertxHttpEndpoint
import automorph.transport.websocket.endpoint.VertxWebSocketEndpoint.Context
import automorph.transport.{HttpContext, HttpMethod, CallbackHttpRequestHandler, Protocol}
import automorph.util.Extensions.EffectOps
import io.vertx.core.Handler
import io.vertx.core.buffer.Buffer
import io.vertx.core.http.{HttpServerRequest, ServerWebSocket}
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
  handler: RequestHandler[Effect, Context] = RequestHandler.dummy[Effect, Context],
) extends ServerTransport[Effect, Context, Handler[ServerWebSocket]] {

  private lazy val requestHandler = new Handler[ServerWebSocket] {

    override def handle(session: ServerWebSocket): Unit = {
      session.binaryMessageHandler { buffer =>
        webSocketHandler.processRequest((buffer, session), session).runAsync
      }
      ()
    }
  }

  private val webSocketHandler =
    CallbackHttpRequestHandler(receiveRequest, sendResponse, Protocol.WebSocket, effectSystem, _ => 0, handler)
  implicit private val system: EffectSystem[Effect] = effectSystem

  override def adapter: Handler[ServerWebSocket] =
    requestHandler

  override def init(): Effect[Unit] =
    effectSystem.successful {}

  override def close(): Effect[Unit] =
    effectSystem.successful {}

  override def requestHandler(handler: RequestHandler[Effect, Context]): VertxWebSocketEndpoint[Effect] =
    copy(handler = handler)

  private def receiveRequest(
    incomingRequest: (Buffer, ServerWebSocket)
  ): (RequestMetadata[Context], Effect[Array[Byte]]) = {
    val (body, webSocket) = incomingRequest
    val requestMetadata = RequestMetadata(
      getRequestContext(webSocket),
      webSocketHandler.protocol,
      webSocket.uri,
      Some(HttpMethod.Get.name),
    )
    lazy val requestBody = effectSystem.evaluate(body.getBytes)
    (requestMetadata, requestBody)
  }

  private def sendResponse(responseData: ResponseMetadata[Context], session: ServerWebSocket): Effect[Unit] =
    effectSystem.completable[Unit].flatMap { completable =>
      session.writeBinaryMessage(Buffer.buffer(responseData.body))
        .onSuccess(_ => completable.succeed(()).runAsync)
        .onFailure(error => completable.fail(error).runAsync)
      completable.effect
    }

  private def getRequestContext(webSocket: ServerWebSocket): Context = {
    val headers = webSocket.headers.entries.asScala.map(entry => entry.getKey -> entry.getValue).toSeq
    HttpContext(
      transportContext = Some(Right(webSocket).withLeft[HttpServerRequest]),
      headers = headers,
      peerId = Some(clientId(webSocket)),
    ).url(
      webSocket.uri
    )
  }

  private def clientId(webSocket: ServerWebSocket): String =
    VertxHttpEndpoint.clientId(webSocket.headers, webSocket.remoteAddress)
}

object VertxWebSocketEndpoint {

  /** Request context type. */
  type Context = HttpContext[Either[HttpServerRequest, ServerWebSocket]]
}
