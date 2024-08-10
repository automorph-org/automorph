package automorph.transport.server

import automorph.log.{Logger, Logging}
import automorph.spi.{EffectSystem, RpcHandler, ServerTransport}
import automorph.transport.ClientServerHttpHandler.RpcCallId
import automorph.transport.HttpContext.headerRpcNodeId
import automorph.transport.HttpMetadata.headerXForwardedFor
import automorph.transport.server.NanoHTTPD.Response.Status
import automorph.transport.server.NanoHTTPD.{IHTTPSession, Response, newFixedLengthResponse}
import automorph.transport.server.NanoServer.{Context, WebSocketListener, WebSocketRequest}
import automorph.transport.server.NanoWSD.WebSocketFrame.CloseCode
import automorph.transport.server.NanoWSD.{WebSocket, WebSocketFrame}
import automorph.transport.{ClientServerHttpHandler, HttpContext, HttpMetadata, HttpMethod, Protocol, ServerHttpHandler}
import automorph.util.Extensions.{ByteArrayOps, EffectOps}
import java.io.IOException
import java.net.{SocketException, URI}
import java.util.concurrent.atomic.AtomicReference
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
  private var rpcHandler: RpcHandler[Effect, Context] = RpcHandler.dummy

  private var httpHandler =
    ClientServerHttpHandler(receiveHttpRequest, sendHttpResponse, Protocol.Http, effectSystem, mapException, rpcHandler)

  private var webSocketHandler = ClientServerHttpHandler(
    receiveWebSocketRequest,
    sendWebSocketResponse,
    Protocol.WebSocket,
    effectSystem,
    _ => 0,
    rpcHandler,
    Some(getRpcCallId),
    Some(setRpcCallId),
  )

  override def rpcHandler(handler: RpcHandler[Effect, Context]): NanoServer[Effect] = {
    this.rpcHandler = handler
    httpHandler =
      ClientServerHttpHandler(
        receiveHttpRequest,
        sendHttpResponse,
        Protocol.Http,
        effectSystem,
        mapException,
        handler,
      )
    webSocketHandler = ClientServerHttpHandler(
      receiveWebSocketRequest,
      sendWebSocketResponse,
      Protocol.WebSocket,
      effectSystem,
      mapException,
      handler,
    )
    this
  }

  override def adapter: Unit =
    ()

  override def init(): Effect[Unit] =
    system.evaluate(this.synchronized {
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
    system.evaluate(this.synchronized {
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
        httpHandler.processRequest(session, (session, queue)).runAsync
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

  private def receiveHttpRequest(request: IHTTPSession): (Effect[Array[Byte]], Context) = {
    val requestBody = system.evaluate(request.getInputStream.readNBytes(request.getBodySize.toInt))
    requestBody -> getRequestContext(request, client(request))
  }

  private def receiveWebSocketRequest(request: WebSocketRequest): (Effect[Array[Byte]], Context) = {
    val (session, frame) = request
    system.successful(frame.getBinaryPayload) -> getRequestContext(session, client(session))
  }

  private def sendHttpResponse(
    body: Array[Byte],
    metadata: HttpMetadata[Context],
    channel: (IHTTPSession, BlockingQueue[Response]),
  ): Effect[Unit] =
    system.evaluate {
      val (_, queue) = channel
      val response = newFixedLengthResponse(
        Status.lookup(metadata.statusCodeOrOk),
        metadata.contentType,
        body.toInputStream,
        body.length.toLong,
      )
      setResponseContext(response, metadata.context)
      queue.add(response)
      ()
    }

  private def sendWebSocketResponse(
    body: Array[Byte],
    @unused metadata: HttpMetadata[Context],
    connection: WebSocketListener[Effect],
  ): Effect[Unit] =
    system.evaluate(connection.send(body))

  private def getRpcCallId(connection: WebSocketListener[Effect]): Option[String] =
    connection.rpcCallId.get

  private def setRpcCallId(connection: WebSocketListener[Effect], id: Option[String]): Unit =
    connection.rpcCallId.set(id)

  private def getRequestContext(session: IHTTPSession, peerId: String): Context = {
    val query = Option(session.getQueryParameterString).filter(_.nonEmpty).map("?" + _).getOrElse("")
    val http = HttpContext(
      transportContext = Some(session),
      method = Some(HttpMethod.valueOf(session.getMethod.name)),
      headers = session.getHeaders.asScala.toSeq,
      peer = Some(peerId),
    ).url(s"${session.getUri}$query").scheme("http").host("localhost").port(port)
    Option(session.getQueryParameterString).map(http.query).getOrElse(http)
  }

  private def setResponseContext(response: Response, context: Context): Unit =
    context.headers.foreach { case (name, value) => response.addHeader(name, value) }

  private def client(session: IHTTPSession): String = {
    val address = Option(session.getRemoteIpAddress).filter(_.nonEmpty).getOrElse(session.getRemoteHostName)
    val forwardedFor = Option(session.getHeaders.get(headerXForwardedFor))
    val nodeId = Option(session.getHeaders.get(headerRpcNodeId))
    ServerHttpHandler.client(address, forwardedFor, nodeId)
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
    handler: ClientServerHttpHandler[Effect, Context, WebSocketRequest, WebSocketListener[Effect]],
    logger: Logger,
    rpcCallId: RpcCallId = new AtomicReference(None),
  ) extends WebSocket(session) {
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
      if (!error.isInstanceOf[SocketException]) {
        handler.failedReceiveWebSocketRequest(error)
      }
  }
}
