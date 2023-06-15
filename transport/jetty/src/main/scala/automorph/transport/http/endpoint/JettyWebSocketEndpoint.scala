package automorph.transport.http.endpoint

import automorph.log.{LogProperties, Logging, MessageLog}
import automorph.spi.{EffectSystem, EndpointTransport, RequestHandler}
import automorph.transport.http.endpoint.JettyHttpEndpoint.Context
import automorph.transport.http.endpoint.JettyWebSocketEndpoint.ResponseCallback
import automorph.transport.http.{HttpContext, HttpMethod, Protocol}
import automorph.util.Extensions.{ByteArrayOps, EffectOps, StringOps, ThrowableOps}
import automorph.util.{Network, Random}
import org.eclipse.jetty.http.HttpHeader
import org.eclipse.jetty.websocket.api.{Session, UpgradeRequest, WebSocketAdapter, WriteCallback}
import org.eclipse.jetty.websocket.server.{JettyServerUpgradeRequest, JettyServerUpgradeResponse, JettyWebSocketCreator}
import scala.collection.immutable.ListMap
import scala.jdk.CollectionConverters.{ListHasAsScala, MapHasAsScala}
import scala.util.Try

/**
 * Jetty WebSocket endpoint message transport plugin.
 *
 * Interprets WebSocket request message as an RPC request and processes it using the specified RPC request handler.
 * - The response returned by the RPC request handler is used as WebSocket response message.
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
  mapException: Throwable => Int = HttpContext.defaultExceptionToStatusCode,
  handler: RequestHandler[Effect, Context] = RequestHandler.dummy[Effect, Context],
) extends WebSocketAdapter
  with JettyWebSocketCreator
  with Logging
  with EndpointTransport[Effect, Context, WebSocketAdapter] {

  private val log = MessageLog(logger, Protocol.Http.name)
  private implicit val system: EffectSystem[Effect] = effectSystem

  /** Jetty WebSocket creator. */
  def creator: JettyWebSocketCreator =
    this

  override def createWebSocket(request: JettyServerUpgradeRequest, response: JettyServerUpgradeResponse): AnyRef =
    adapter

  override def adapter: WebSocketAdapter =
    this

  override def withHandler(handler: RequestHandler[Effect, Context]): JettyWebSocketEndpoint[Effect] =
    copy(handler = handler)

  override def onWebSocketText(message: String): Unit =
    handle(message.toByteArray)

  override def onWebSocketBinary(payload: Array[Byte], offset: Int, length: Int): Unit =
    handle(payload)

  private def handle(requestBody: Array[Byte]): Unit = {
    // Log the request
    val session = getSession
    val requestId = Random.id
    lazy val requestProperties = getRequestProperties(session, requestId)
    log.receivedRequest(requestProperties)

    // Process the request
    Try {
      val handlerResult = handler.processRequest(requestBody, getRequestContext(session.getUpgradeRequest), requestId)
      handlerResult.either.map(
        _.fold(
          error => sendErrorResponse(error, session, requestId, requestProperties),
          result => {
            // Send the response
            val responseBody = result.map(_.responseBody).getOrElse(Array.emptyByteArray)
            sendResponse(responseBody, session, requestId)
          },
        )
      ).runAsync
    }.failed.foreach { error =>
      sendErrorResponse(error, session, requestId, requestProperties)
    }
  }

  private def sendErrorResponse(
    error: Throwable,
    session: Session,
    requestId: String,
    requestProperties: => Map[String, String],
  ): Unit = {
    log.failedProcessRequest(error, requestProperties)
    val responseBody = error.description.toByteArray
    sendResponse(responseBody, session, requestId)
  }

  private def sendResponse(responseBody: Array[Byte], session: Session, requestId: String): Unit = {
    // Log the response
    lazy val responseProperties = ListMap(
      LogProperties.requestId -> requestId,
      LogProperties.client -> clientAddress(session),
    )
    log.sendingResponse(responseProperties)

    // Send the response
    session.getRemote.sendBytes(responseBody.toByteBuffer, ResponseCallback(log, responseProperties))
  }

  private def getRequestContext(request: UpgradeRequest): Context = {
    val headers = request.getHeaders.asScala.flatMap { case (name, values) =>
      values.asScala.map(name -> _)
    }.toSeq
    HttpContext(transportContext = None, method = Some(HttpMethod.valueOf(request.getMethod)), headers = headers)
      .url(request.getRequestURI)
  }

  private def getRequestProperties(session: Session, requestId: String): Map[String, String] = {
    val request = session.getUpgradeRequest
    val query = Option(request.getQueryString).filter(_.nonEmpty).map("?" + _).getOrElse("")
    val url = s"${request.getRequestURI}$query"
    ListMap(
      LogProperties.requestId -> requestId,
      LogProperties.client -> clientAddress(session),
      "URL" -> url,
      "Method" -> request.getMethod,
    )
  }

  private def clientAddress(session: Session): String = {
    val forwardedFor = Option(session.getUpgradeRequest.getHeader(HttpHeader.X_FORWARDED_FOR.name))
    val address = session.getRemoteAddress.toString
    Network.address(forwardedFor, address)
  }
}

case object JettyWebSocketEndpoint {

  /** Request context type. */
  type Context = JettyHttpEndpoint.Context

  private final case class ResponseCallback(
    log: MessageLog,
    responseProperties: ListMap[String, String],
  ) extends WriteCallback {
    override def writeFailed(error: Throwable): Unit =
      log.failedSendResponse(error, responseProperties)

    override def writeSuccess(): Unit =
      log.sentResponse(responseProperties)
  }
}
