package automorph.transport.server

import automorph.log.Logging
import automorph.spi.{EffectSystem, RequestHandler, ServerTransport}
import automorph.transport.HttpRequestHandler.{RequestData, ResponseData}
import automorph.transport.server.JettyHttpEndpoint.{Context, requestQuery}
import automorph.transport.server.JettyWebSocketEndpoint.ResponseCallback
import automorph.transport.{HttpContext, HttpMethod, HttpRequestHandler, Protocol}
import automorph.util.Extensions.{ByteArrayOps, EffectOps, StringOps}
import automorph.util.Network
import org.eclipse.jetty.http.HttpHeader
import org.eclipse.jetty.websocket.api.{Session, UpgradeRequest, WebSocketAdapter, WriteCallback}
import org.eclipse.jetty.websocket.server.{JettyServerUpgradeRequest, JettyServerUpgradeResponse, JettyWebSocketCreator}
import scala.jdk.CollectionConverters.{ListHasAsScala, MapHasAsScala}

/**
 * Jetty WebSocket endpoint message transport plugin.
 *
 * Interprets WebSocket request message as an RPC request and processes it using the specified RPC request handler.
 *   - The response returned by the RPC request handler is used as WebSocket response message.
 *
 * @see
 *   [[https://en.wikipedia.org/wiki/Hypertext Transport protocol]]
 * @see
 *   [[https://www.eclipse.org/jetty Library documentation]]
 * @see
 *   [[https://www.eclipse.org/jetty/javadoc/jetty-11/index.html API]]
 * @constructor
 *   Creates an Jetty HTTP endpoint message transport plugin with specified effect system and request handler.
 * @param effectSystem
 *   effect system plugin
 * @param mapException
 *   maps an exception to a corresponding HTTP status code
 * @param handler
 *   RPC request handler
 * @tparam Effect
 *   effect type
 */
final case class JettyWebSocketEndpoint[Effect[_]](
  effectSystem: EffectSystem[Effect],
  mapException: Throwable => Int = HttpContext.toStatusCode,
  handler: RequestHandler[Effect, Context] = RequestHandler.dummy[Effect, Context],
) extends ServerTransport[Effect, Context, WebSocketAdapter] with Logging {
  private lazy val webSocketAdapter = new WebSocketAdapter {
    implicit private val system: EffectSystem[Effect] = effectSystem

    override def onWebSocketText(message: String): Unit = {
      val session = getSession
      webSocketHandler.processRequest((message.toByteArray, session), session).runAsync
    }

    override def onWebSocketBinary(payload: Array[Byte], offset: Int, length: Int): Unit = {
      val session = getSession
      webSocketHandler.processRequest((payload, session), session).runAsync
    }
  }
  private lazy val jettyWebSocketCreator = new JettyWebSocketCreator {

    override def createWebSocket(request: JettyServerUpgradeRequest, response: JettyServerUpgradeResponse): AnyRef =
      adapter
  }
  private val webSocketHandler = HttpRequestHandler(
    receiveRequest,
    sendResponse,
    Protocol.WebSocket,
    effectSystem,
    mapException,
    handler,
    logger,
    logResponse = false,
  )

  /** Jetty WebSocket creator. */
  def creator: JettyWebSocketCreator =
    jettyWebSocketCreator

  override def adapter: WebSocketAdapter =
    webSocketAdapter

  override def init(): Effect[Unit] =
    effectSystem.successful {}

  override def close(): Effect[Unit] =
    effectSystem.successful {}

  override def requestHandler(handler: RequestHandler[Effect, Context]): JettyWebSocketEndpoint[Effect] =
    copy(handler = handler)

  private def receiveRequest(incomingRequest: (Array[Byte], Session)): RequestData[Context] = {
    val (requestBody, session) = incomingRequest
    val request = session.getUpgradeRequest
    val query = requestQuery(request.getQueryString)
    RequestData(
      () => requestBody,
      getRequestContext(request),
      webSocketHandler.protocol,
      s"${request.getRequestURI.toString}$query",
      clientAddress(session),
      Some(request.getMethod),
    )
  }

  private def sendResponse(
    responseData: ResponseData[Context],
    session: Session,
    logResponse: Option[Throwable] => Unit,
  ): Effect[Unit] =
    effectSystem.evaluate(
      session.getRemote.sendBytes(responseData.body.toByteBuffer, ResponseCallback(logResponse))
    )

  private def getRequestContext(request: UpgradeRequest): Context = {
    val headers = request.getHeaders.asScala.flatMap { case (name, values) =>
      values.asScala.map(name -> _)
    }.toSeq
    HttpContext(transportContext = None, method = Some(HttpMethod.valueOf(request.getMethod)), headers = headers)
      .url(request.getRequestURI)
  }

  private def clientAddress(session: Session): String = {
    val forwardedFor = Option(session.getUpgradeRequest.getHeader(HttpHeader.X_FORWARDED_FOR.name))
    val address = session.getRemoteAddress.toString
    Network.address(forwardedFor, address)
  }
}

object JettyWebSocketEndpoint {

  /** Request context type. */
  type Context = JettyHttpEndpoint.Context

  final private case class ResponseCallback(logResponse: Option[Throwable] => Unit) extends WriteCallback {

    override def writeSuccess(): Unit =
      logResponse(None)

    override def writeFailed(error: Throwable): Unit =
      logResponse(Some(error))
  }
}
