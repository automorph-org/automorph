//package automorph.transport.server
//
//import automorph.log.{Logger, MessageLog}
//import automorph.spi.EffectSystem.Completable
//import automorph.spi.{EffectSystem, RpcHandler, ServerTransport}
//import automorph.transport.ClientServerHttpHandler.RpcCallId
//import automorph.transport.HttpClientBase.{completableEffect, webSocketConnectionClosed, webSocketUnexpectedMessage}
//import automorph.transport.HttpContext.headerRpcNodeId
//import automorph.transport.server.JettyHttpEndpoint.{Context, getRequestContext}
//import automorph.transport.server.JettyWebSocketEndpoint.WebSocketRequest
//import automorph.transport.{ClientServerHttpHandler, HttpContext, HttpMetadata, HttpMethod, Protocol, ServerHttpHandler}
//import automorph.util.Extensions.{ByteArrayOps, ByteBufferOps, EffectOps, StringOps}
//import org.eclipse.jetty.http.HttpHeader
//import org.eclipse.jetty.io.Content
//import org.eclipse.jetty.server.{Handler, Request, Response}
//import org.eclipse.jetty.util
//import org.eclipse.jetty.websocket.api.annotations.{OnWebSocketError, OnWebSocketMessage, WebSocket}
//import org.eclipse.jetty.websocket.api.{Callback, Session}
//import java.net.URI
//import java.nio.ByteBuffer
//import java.util.concurrent.atomic.AtomicReference
//import scala.annotation.unused
//import scala.jdk.CollectionConverters.{ListHasAsScala, MapHasAsScala}
//
///**
// * Jetty WebSocket endpoint message transport plugin.
// *
// * Interprets WebSocket request message as an RPC request and processes it using the specified RPC request handler.
// *   - The response returned by the RPC request handler is used as WebSocket response message.
// *
// * @see
// *   [[https://en.wikipedia.org/wiki/Hypertext Transport protocol]]
// * @see
// *   [[https://www.eclipse.org/jetty Library documentation]]
// * @see
// *   [[https://www.eclipse.org/jetty/javadoc/jetty-11/index.html API]]
// * @constructor
// *   Creates an Jetty HTTP endpoint message transport plugin with specified effect system and request handler.
// * @param effectSystem
// *   effect system plugin
// * @param mapException
// *   maps an exception to a corresponding HTTP status code
// * @param rpcHandler
// *   RPC request handler
// * @tparam Effect
// *   effect type
// */
//final case class JettyWebSocketEndpoint[Effect[_]](
//  effectSystem: EffectSystem[Effect],
//  mapException: Throwable => Int = HttpContext.toStatusCode,
//  rpcHandler: RpcHandler[Effect, Context] = RpcHandler.dummy[Effect, Context],
//) extends ServerTransport[Effect, Context, Handler] {
//  private lazy val webSocketHandler = new Handler.Abstract() {
//    implicit private val system: EffectSystem[Effect] = effectSystem
//
//    override def handle(request: Request, response: Response, callback: util.Callback): Boolean = {
//      handler.processRequest(request, response).fold(callback.failed, _ => callback.succeeded()).runAsync
//      true
//    }
//  }
//  private val handler =
//    ClientServerHttpHandler(receiveRequest, sendResponse, Protocol.WebSocket, effectSystem, mapException, rpcHandler)
//  implicit private val system: EffectSystem[Effect] = effectSystem
//
//  override def adapter: Handler =
//    webSocketHandler
//
//  override def init(): Effect[Unit] =
//    effectSystem.successful {}
//
//  override def close(): Effect[Unit] =
//    effectSystem.successful {}
//
//  override def rpcHandler(handler: RpcHandler[Effect, Context]): JettyWebSocketEndpoint[Effect] =
//    copy(rpcHandler = handler)
//
//  private def receiveRequest(request: Request): (Effect[Array[Byte]], Context) = {
//    val requestBody = completableEffect(Content.Source.asByteBufferAsync(request), system).map(_.toByteArray)
//    requestBody -> getRequestContext(request)
//  }
//
//  private def sendResponse(
//    body: Array[Byte],
//    @unused metadata: HttpMetadata[Context],
//    connection: (Session, RpcCallId),
//  ): Effect[Unit] = {
//    val (session, _) = connection
//    effectSystem.completable[Unit].flatMap { expectedResponseSent =>
//      val responseCallback = ResponseCallback(expectedResponseSent, effectSystem, handler)
//      session.getRemote.sendBytes(body.toByteBuffer, responseCallback)
//      expectedResponseSent.effect
//    }
//  }
//}
//
//object JettyWebSocketEndpoint {
//
//  /** Request context type. */
//  type Context = JettyHttpEndpoint.Context
//
//  private type WebSocketRequest = (Array[Byte], Session)
//
//  @WebSocket(autoDemand = true)
//  final case class FrameListener[Effect[_]](
//    url: URI,
//    removeConnection: () => Unit,
//    effectSystem: EffectSystem[Effect],
//    handler: ClientServerHttpHandler[Effect, Context, WebSocketRequest, (Session, RpcCallId)],
//    rpcCallId: RpcCallId = new AtomicReference(None),
//  ) {
//    implicit val system: EffectSystem[Effect] = effectSystem
//
//    @OnWebSocketMessage
//    def onWebSocketBinary(payload: ByteBuffer, callback: Callback): Unit = {
//      val requestBody = payload.toByteArray
//      callback.succeed()
//      handler.processRequest(requestBody -> session, session -> rpcCallId).runAsync
//    }
//
//    @OnWebSocketMessage
//    def onWebSocketText(message: String): Unit =
//      handler.processRequest(message.toByteArray -> session, session -> rpcCallId).runAsync
//
//    @OnWebSocketError
//    def onWebSocketError(error: Throwable): Unit =
//      handler.failedReceiveWebSocketRequest(error)
//    }
//  }
//
////  final private case class WebSocketListener[Effect[_]](
////    effectSystem: EffectSystem[Effect],
////    handler: ClientServerHttpHandler[Effect, Context, WebSocketRequest, (Session, RpcCallId)],
////    rpcCallId: RpcCallId = new AtomicReference(None),
////  ) extends Session.Listener {
////    implicit private val system: EffectSystem[Effect] = effectSystem
////
////    override def onWebSocketBinary(payload: Array[Byte], offset: Int, length: Int): Unit = {
////      val session = getSession
////      handler.processRequest(payload -> session, session -> rpcCallId).runAsync
////    }
////
////    override def onWebSocketText(message: String): Unit = {
////      val session = getSession
////      handler.processRequest(message.toByteArray -> session, session -> rpcCallId).runAsync
////    }
////
////    override def onWebSocketError(cause: Throwable): Unit = {
////      handler.failedReceiveWebSocketRequest(cause)
////      super.onWebSocketError(cause)
////    }
////  }
////
////  final private case class ResponseCallback[Effect[_]](
////    expectedResponseSent: Completable[Effect, Unit],
////    effectSystem: EffectSystem[Effect],
////    handler: ClientServerHttpHandler[Effect, Context, WebSocketRequest, (Session, RpcCallId)],
////  ) extends WriteCallback {
////    implicit private val system: EffectSystem[Effect] = effectSystem
////
////    override def writeSuccess(): Unit =
////      expectedResponseSent.succeed(()).runAsync
////
////    override def writeFailed(error: Throwable): Unit =
////      expectedResponseSent.fail(error).runAsync
////  }
//}
