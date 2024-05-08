package automorph.transport.server

import automorph.log.{Logger, Logging, MessageLog}
import automorph.spi.{EffectSystem, RequestHandler, ServerTransport}
import automorph.transport.HttpRequestHandler.{RequestData, ResponseData}
import automorph.transport.server.NanoHTTPD.Response.Status
import automorph.transport.server.NanoHTTPD.{IHTTPSession, Response, newFixedLengthResponse}
import automorph.transport.server.NanoServer.{Context, WebSocketListener, WebSocketRequest}
import automorph.transport.server.NanoWSD.WebSocketFrame.CloseCode
import automorph.transport.server.NanoWSD.{WebSocket, WebSocketFrame}
import automorph.transport.{HttpContext, HttpMethod, HttpRequestHandler, Protocol}
import automorph.util.Extensions.{ByteArrayOps, EffectOps}
import automorph.util.Network
import java.io.IOException
import java.net.URI
import java.util.concurrent.{ArrayBlockingQueue, BlockingQueue}
import scala.annotation.unused
import scala.collection.immutable.ListMap
import scala.concurrent.duration.{DurationInt, FiniteDuration}
import scala.jdk.CollectionConverters.MapHasAsScala

/**
 * NanoHTTPD HTTP & WebSocket server message transport plugin.
 *
 * Interprets HTTP request body as an RPC request and processes it using the specified RPC request handler.
 *   - The response returned by the RPC request handler is used as HTTP response body.
 *   - Processes only HTTP requests starting with specified URL path.
 *
 * @see
 *   [[https://en.wikipedia.org/wiki/HTTP Transport protocol]]
 * @see
 *   [[https://github.com/NanoHttpd/nanohttpd Library documentation]]
 * @see
 *   [[https://javadoc.io/doc/org.nanohttpd/nanohttpd/latest/index.html API]]
 * @constructor
 *   Creates a NanoHTTPD HTTP & WebSocket server with specified effect system.
 * @param effectSystem
 *   effect system plugin
 * @param port
 *   port to listen on for HTTP connections
 * @param pathPrefix
 *   HTTP URL path prefix, only requests starting with this path prefix are allowed
 * @param methods
 *   allowed HTTP request methods
 * @param webSocket
 *   support upgrading of HTTP connections to use WebSocket protocol if true, support HTTP only if false
 * @param mapException
 *   maps an exception to a corresponding HTTP status code
 * @param readTimeout
 *   request read timeout
 * @param threads
 *   number of request processing threads
 * @tparam Effect
 *   effect type
 */
final case class NanoServer[Effect[_]](
  effectSystem: EffectSystem[Effect],
  port: Int,
  pathPrefix: String = "/",
  methods: Iterable[HttpMethod] = HttpMethod.values,
  webSocket: Boolean = true,
  mapException: Throwable => Int = HttpContext.toStatusCode,
  readTimeout: FiniteDuration = 30.seconds,
  threads: Int = Runtime.getRuntime.availableProcessors * 2,
) extends NanoWSD(port, threads) with Logging with ServerTransport[Effect, Context, Unit] {
  private val allowedMethods = methods.map(_.name).toSet
  implicit private val system: EffectSystem[Effect] = effectSystem
  private var handler: RequestHandler[Effect, Context] = RequestHandler.dummy

  private var httpHandler =
    HttpRequestHandler(receiveHttpRequest, sendHttpResponse, Protocol.Http, effectSystem, mapException, handler, logger)

  private var webSocketHandler = HttpRequestHandler(
    receiveWebSocketRequest,
    sendWebSocketResponse,
    Protocol.WebSocket,
    effectSystem,
    _ => 0,
    handler,
    logger,
  )

  override def requestHandler(handler: RequestHandler[Effect, Context]): NanoServer[Effect] = {
    this.handler = handler
    httpHandler = HttpRequestHandler(
      receiveHttpRequest,
      sendHttpResponse,
      Protocol.Http,
      effectSystem,
      mapException,
      handler,
      logger,
    )
    webSocketHandler =
      HttpRequestHandler(
        receiveWebSocketRequest,
        sendWebSocketResponse,
        Protocol.WebSocket,
        effectSystem,
        mapException,
        handler,
        logger,
      )
    this
  }

  override def adapter: Unit =
    ()

  override def init(): Effect[Unit] =
    effectSystem.evaluate(this.synchronized {
      super.start(readTimeout.toMillis.toInt)
      (Seq(Protocol.Http) ++ Option.when(webSocket)(Protocol.WebSocket)).foreach { protocol =>
        logger.info(
          "Listening for connections",
          ListMap(
            "Protocol" -> protocol,
            "Port" -> port.toString,
          ),
        )
      }
    })

  override def close(): Effect[Unit] =
    effectSystem.evaluate(this.synchronized {
      if (!isAlive) {
        throw new IllegalStateException(s"${getClass.getSimpleName} already closed")
      }
      stop()
    })

  /**
   * Serve HTTP session.
   *
   * @param session
   *   HTTP session
   * @return
   *   HTTP response
   */
  override protected def serveHttp(session: IHTTPSession): BlockingQueue[Response] = {
    // Validate URL path
    val queue = new ArrayBlockingQueue[Response](1)
    val url = new URI(session.getUri)
    if (!url.getPath.startsWith(pathPrefix)) {
      queue.add(newFixedLengthResponse(Status.NOT_FOUND, NanoHTTPD.MIME_PLAINTEXT, "Not Found"))
    } else {
      // Validate HTTP request method
      if (!allowedMethods.contains(session.getMethod.toString.toUpperCase)) {
        queue.add(newFixedLengthResponse(Status.METHOD_NOT_ALLOWED, NanoHTTPD.MIME_PLAINTEXT, "Method Not Allowed"))
      } else {
        httpHandler.processRequest(session, session).map(response => queue.add(response)).runAsync
      }
    }
    queue
  }

  /**
   * Serve WebSocket handshake session.
   *
   * @param session
   *   WebSocket handshake session
   * @return
   *   WebSocket handler
   */
  override protected def openWebSocket(session: IHTTPSession): WebSocket =
    WebSocketListener(session, webSocket, effectSystem, webSocketHandler, logger)

  private def receiveHttpRequest(request: IHTTPSession): (RequestData[Context], Effect[Array[Byte]]) = {
    val query = Option(request.getQueryParameterString).filter(_.nonEmpty).map("?" + _).getOrElse("")
    val requestData = RequestData(
      getRequestContext(request),
      httpHandler.protocol,
      s"${request.getUri}$query",
      clientAddress(request),
      Some(request.getMethod.toString),
    )
    val requestBody = effectSystem.evaluate(request.getInputStream.readNBytes(request.getBodySize.toInt))
    (requestData, requestBody)
  }

  private def receiveWebSocketRequest(request: WebSocketRequest): (RequestData[Context], Effect[Array[Byte]]) = {
    val (session, frame) = request
    val query = Option(session.getQueryParameterString).filter(_.nonEmpty).map("?" + _).getOrElse("")
    val requestData = RequestData(
      getRequestContext(session),
      Protocol.WebSocket,
      s"${session.getUri}$query",
      clientAddress(session),
    )
    val requestBody = effectSystem.successful(frame.getBinaryPayload)
    (requestData, requestBody)
  }

  private def sendHttpResponse(responseData: ResponseData[Context], @unused channel: IHTTPSession): Effect[Response] = {
    val response = newFixedLengthResponse(
      Status.lookup(responseData.statusCode),
      responseData.contentType,
      responseData.body.toInputStream,
      responseData.body.length.toLong,
    )
    setResponseContext(response, responseData.context)
    effectSystem.successful(response)
  }

  private def sendWebSocketResponse(responseData: ResponseData[Context], channel: WebSocket): Effect[Unit] =
    effectSystem.evaluate(channel.send(responseData.body))

  private def getRequestContext(session: IHTTPSession): Context = {
    val http = HttpContext(
      transportContext = Some(session),
      method = Some(HttpMethod.valueOf(session.getMethod.name)),
      headers = session.getHeaders.asScala.toSeq,
    ).url(session.getUri).scheme("http").host("localhost").port(port)
    Option(session.getQueryParameterString).map(http.query).getOrElse(http)
  }

  private def setResponseContext(response: Response, context: Option[Context]): Unit =
    context.toSeq.flatMap(_.headers).foreach { case (name, value) => response.addHeader(name, value) }

  private def clientAddress(session: IHTTPSession): String = {
    val forwardedFor = Option(session.getHeaders.get(HttpRequestHandler.headerXForwardedFor))
    val address = Option(session.getRemoteIpAddress).filter(_.nonEmpty).getOrElse(session.getRemoteHostName)
    Network.address(forwardedFor, address)
  }
}

object NanoServer {

  /** Request context type. */
  type Context = HttpContext[IHTTPSession]

  private type WebSocketRequest = (IHTTPSession, WebSocketFrame)

  final private case class WebSocketListener[Effect[_]](
    session: IHTTPSession,
    webSocket: Boolean,
    effectSystem: EffectSystem[Effect],
    handler: HttpRequestHandler[Effect, Context, WebSocketRequest, Unit, WebSocket],
    logger: Logger,
  ) extends WebSocket(session) {
    private val log = MessageLog(logger, Protocol.WebSocket.name)
    implicit private val system: EffectSystem[Effect] = effectSystem

    override protected def onOpen(): Unit =
      if (!webSocket) {
        this.close(CloseCode.PolicyViolation, "WebSocket support disabled", true)
      }

    override protected def onClose(code: WebSocketFrame.CloseCode, reason: String, initiatedByRemote: Boolean): Unit =
      ()

    protected def onMessage(frame: WebSocketFrame): Unit =
      handler.processRequest((session, frame), this).runAsync

    override protected def onPong(pong: WebSocketFrame): Unit =
      ()

    override protected def onException(error: IOException): Unit =
      log.failedReceiveRequest(error, Map.empty, Protocol.WebSocket.name)
  }
}
