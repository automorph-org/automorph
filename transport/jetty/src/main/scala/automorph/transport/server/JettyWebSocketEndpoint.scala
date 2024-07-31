//package automorph.transport.server
//
//import automorph.spi.EffectSystem.Completable
//import automorph.spi.{EffectSystem, RpcHandler, ServerTransport}
//import automorph.transport.ClientServerHttpHandler.RpcCallId
//import automorph.transport.HttpContext.headerRpcNodeId
//import automorph.transport.server.JettyHttpEndpoint.{Context, requestQuery}
//import automorph.transport.server.JettyWebSocketEndpoint.{ResponseCallback, WebSocketListener, WebSocketRequest}
//import automorph.transport.{ClientServerHttpHandler, HttpContext, HttpMetadata, HttpMethod, Protocol, ServerHttpHandler}
//import automorph.util.Extensions.{ByteArrayOps, EffectOps, StringOps}
//import org.eclipse.jetty.http.HttpHeader
//import org.eclipse.jetty.websocket.api.{Session, WebSocketAdapter, WriteCallback}
//import org.eclipse.jetty.websocket.server.{JettyServerUpgradeRequest, JettyServerUpgradeResponse, JettyWebSocketCreator}
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
//) extends ServerTransport[Effect, Context, WebSocketAdapter] {
//  private lazy val webSocketAdapter = WebSocketListener(effectSystem, handler)
//  private lazy val jettyWebSocketCreator = new JettyWebSocketCreator {
//
//    override def createWebSocket(request: JettyServerUpgradeRequest, response: JettyServerUpgradeResponse): AnyRef =
//      adapter
//  }
//  private val handler =
//    ClientServerHttpHandler(receiveRequest, sendResponse, Protocol.WebSocket, effectSystem, mapException, rpcHandler)
//  implicit private val system: EffectSystem[Effect] = effectSystem
//
//  /** Jetty WebSocket creator. */
//  def creator: JettyWebSocketCreator =
//    jettyWebSocketCreator
//
//  override def adapter: WebSocketAdapter =
//    webSocketAdapter
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
//  private def receiveRequest(incomingRequest: WebSocketRequest): (Effect[Array[Byte]], Context) = {
//    val (body, session) = incomingRequest
//    effectSystem.successful(body) -> getRequestContext(session)
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
//
//  private def getRequestContext(session: Session): Context = {
//    val request = session.getUpgradeRequest
//    val headers = request.getHeaders.asScala.flatMap { case (name, values) =>
//      values.asScala.map(name -> _)
//    }.toSeq
//    val query = requestQuery(request.getQueryString)
//    HttpContext(
//      transportContext = None,
//      method = Some(HttpMethod.valueOf(request.getMethod)),
//      headers = headers,
//      peer = Some(client(session)),
//    ).url(s"${request.getRequestURI.toString}$query")
//  }
//
//  private def client(session: Session): String = {
//    val address = session.getRemoteAddress.toString
//    val forwardedFor = Option(session.getUpgradeRequest.getHeader(HttpHeader.X_FORWARDED_FOR.name))
//    val nodeId = Option(session.getUpgradeRequest.getHeader(headerRpcNodeId))
//    ServerHttpHandler.client(address, forwardedFor, nodeId)
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
//  final private case class WebSocketListener[Effect[_]](
//    effectSystem: EffectSystem[Effect],
//    handler: ClientServerHttpHandler[Effect, Context, WebSocketRequest, (Session, RpcCallId)],
//    rpcCallId: RpcCallId = new AtomicReference(None),
//  ) extends WebSocketAdapter {
//    implicit private val system: EffectSystem[Effect] = effectSystem
//
//    override def onWebSocketBinary(payload: Array[Byte], offset: Int, length: Int): Unit = {
//      val session = getSession
//      handler.processRequest(payload -> session, session -> rpcCallId).runAsync
//    }
//
//    override def onWebSocketText(message: String): Unit = {
//      val session = getSession
//      handler.processRequest(message.toByteArray -> session, session -> rpcCallId).runAsync
//    }
//
//    override def onWebSocketError(cause: Throwable): Unit = {
//      handler.failedReceiveWebSocketRequest(cause)
//      super.onWebSocketError(cause)
//    }
//  }
//
//  final private case class ResponseCallback[Effect[_]](
//    expectedResponseSent: Completable[Effect, Unit],
//    effectSystem: EffectSystem[Effect],
//    handler: ClientServerHttpHandler[Effect, Context, WebSocketRequest, (Session, RpcCallId)],
//  ) extends WriteCallback {
//    implicit private val system: EffectSystem[Effect] = effectSystem
//
//    override def writeSuccess(): Unit =
//      expectedResponseSent.succeed(()).runAsync
//
//    override def writeFailed(error: Throwable): Unit =
//      expectedResponseSent.fail(error).runAsync
//  }
//}
