package automorph.transport.http.server

import automorph.log.{LogProperties, Logging, MessageLog}
import automorph.spi.{EffectSystem, RequestHandler, ServerTransport}
import automorph.transport.http.server.NanoHTTPD.Response.{IStatus, Status}
import automorph.transport.http.server.NanoHTTPD.{IHTTPSession, Response, newFixedLengthResponse}
import automorph.transport.http.server.NanoServer.{Context, HttpResponse}
import automorph.transport.http.server.NanoWSD.WebSocketFrame.CloseCode
import automorph.transport.http.server.NanoWSD.{WebSocket, WebSocketFrame}
import automorph.transport.http.{HttpContext, HttpMethod, Protocol}
import automorph.util.Extensions.{ByteArrayOps, EffectOps, StringOps, ThrowableOps, TryOps}
import automorph.util.{Network, Random}
import java.io.IOException
import java.net.URI
import java.util.concurrent.{ArrayBlockingQueue, BlockingQueue}
import scala.collection.immutable.ListMap
import scala.concurrent.duration.{DurationInt, FiniteDuration}
import scala.jdk.CollectionConverters.MapHasAsScala
import scala.util.Try

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
final case class NanoServer[Effect[_]] (
  effectSystem: EffectSystem[Effect],
  port: Int,
  pathPrefix: String = "/",
  methods: Iterable[HttpMethod] = HttpMethod.values,
  webSocket: Boolean = true,
  mapException: Throwable => Int = HttpContext.defaultExceptionToStatusCode,
  readTimeout: FiniteDuration = 30.seconds,
  threads: Int = Runtime.getRuntime.availableProcessors * 2,
) extends NanoWSD(port, threads) with Logging with ServerTransport[Effect, Context] {

  private var handler: RequestHandler[Effect, Context] = RequestHandler.dummy
  private val headerXForwardedFor = "X-Forwarded-For"
  private val log = MessageLog(logger, Protocol.Http.name)
  private val allowedMethods = methods.map(_.name).toSet
  private implicit val system: EffectSystem[Effect] = effectSystem

  override def withHandler(handler: RequestHandler[Effect, Context]): NanoServer[Effect] = {
    this.handler = handler
    this
  }

  override def init(): Effect[Unit] =
    effectSystem.evaluate(this.synchronized {
      super.start(readTimeout.toMillis.toInt)
      (Seq(Protocol.Http) ++ Option.when(webSocket)(Protocol.WebSocket)).foreach { protocol =>
        logger.info("Listening for connections", ListMap(
          "Protocol" -> protocol,
          "Port" -> port.toString
        ))
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
        queue.add(
          newFixedLengthResponse(Status.METHOD_NOT_ALLOWED, NanoHTTPD.MIME_PLAINTEXT, "Method Not Allowed")
        )
      } else {
        // Receive the request
        val protocol = Protocol.Http
        val requestId = Random.id
        lazy val requestProperties = getRequestProperties(session, protocol, requestId)
        log.receivingRequest(requestProperties, Protocol.Http.name)
        val requestBody = session.getInputStream.readNBytes(session.getBodySize.toInt)

        // Handle the request
        handleRequest(requestBody, session, protocol, requestProperties, requestId).map { response =>
          // Create the response
          val httpResponse = newFixedLengthResponse(
            response.status,
            handler.mediaType,
            response.body.toInputStream,
            response.body.length.toLong,
          )
          setResponseContext(httpResponse, response.context)
          log.sentResponse(response.properties, protocol.name)
          queue.add(httpResponse)
        }.runAsync
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
    new WebSocket(session) {

      override protected def onOpen(): Unit =
        if (!webSocket) {
          this.close(CloseCode.PolicyViolation, "WebSocket support disabled", true)
        }

      override protected def onClose(code: WebSocketFrame.CloseCode, reason: String, initiatedByRemote: Boolean): Unit =
        ()

      protected def onMessage(frame: WebSocketFrame): Unit = {
        // Receive the request
        val protocol = Protocol.WebSocket
        val requestId = Random.id
        lazy val requestProperties = getRequestProperties(session, protocol, requestId)
        val requestBody = frame.getBinaryPayload

        // Handle the request
        handleRequest(requestBody, session, protocol, requestProperties, requestId).map { response =>
          send(response.body)
          log.sentResponse(response.properties, protocol.name)
        }.runAsync
      }

      override protected def onPong(pong: WebSocketFrame): Unit =
        ()

      override protected def onException(error: IOException): Unit =
        log.failedReceiveRequest(error, Map(), Protocol.WebSocket.name)
    }

  private def handleRequest(
    requestBody: Array[Byte],
    session: IHTTPSession,
    protocol: Protocol,
    requestProperties: => Map[String, String],
    requestId: String,
  ): Effect[HttpResponse] = {
    log.receivedRequest(requestProperties, protocol.name)

    // Process the request
    Try {
      val handlerResult = handler.processRequest(requestBody, getRequestContext(session), requestId)
      handlerResult.either.map(
        _.fold(
          error => createErrorResponse(error, session, protocol, requestId, requestProperties),
          result => {
            // Send the response
            val responseBody = result.map(_.responseBody).getOrElse(Array.emptyByteArray)
            val status = result.flatMap(_.exception).map(mapException).map(Status.lookup).getOrElse(Status.OK)
            createResponse(responseBody, status, result.flatMap(_.context), session, protocol, requestId)
          },
        )
      )
    }.foldError { error =>
      effectSystem.successful(createErrorResponse(error, session, protocol, requestId, requestProperties))
    }
  }

  private def createErrorResponse(
    error: Throwable,
    session: IHTTPSession,
    protocol: Protocol,
    requestId: String,
    requestProperties: => Map[String, String],
  ): HttpResponse = {
    log.failedProcessRequest(error, requestProperties, protocol.name)
    val message = error.description.toByteArray
    createResponse(message, Status.INTERNAL_ERROR, None, session, protocol, requestId)
  }

  private def createResponse(
    responseBody: Array[Byte],
    status: Status,
    responseContext: Option[Context],
    session: IHTTPSession,
    protocol: Protocol,
    requestId: String,
  ): HttpResponse = {
    // Log the response
    val responseStatus = responseContext.flatMap(_.statusCode.map(Status.lookup)).getOrElse(status)
    lazy val responseProperties =
      Map(LogProperties.requestId -> requestId, LogProperties.client -> clientAddress(session)) ++
      (protocol match {
        case Protocol.Http => Some("Status" -> responseStatus.toString)
        case _ => None
      })
    log.sendingResponse(responseProperties, protocol.name)
    HttpResponse(responseBody, responseStatus, responseContext, responseProperties)
  }

  private def setResponseContext(response: Response, responseContext: Option[Context]): Unit =
    responseContext.toSeq.flatMap(_.headers).foreach { case (name, value) => response.addHeader(name, value) }

  private def getRequestContext(session: IHTTPSession): Context = {
    val http = HttpContext(
      transportContext = Some(session),
      method = Some(HttpMethod.valueOf(session.getMethod.name)),
      headers = session.getHeaders.asScala.toSeq,
    ).url(session.getUri).scheme("http").host("localhost").port(port)
    Option(session.getQueryParameterString).map(http.query).getOrElse(http)
  }

  private def getRequestProperties(
    session: IHTTPSession,
    protocol: Protocol,
    requestId: String,
  ): Map[String, String] = {
    val query = Option(session.getQueryParameterString).filter(_.nonEmpty).map("?" + _).getOrElse("")
    val url = s"${session.getUri}$query"
    ListMap(
      LogProperties.requestId -> requestId,
      LogProperties.client -> clientAddress(session),
      "Protocol" -> protocol.toString,
      "URL" -> url,
    ) ++ Option.when(protocol == Protocol.Http)("Method" -> session.getMethod.toString)
  }

  private def clientAddress(session: IHTTPSession): String = {
    val forwardedFor = Option(session.getHeaders.get(headerXForwardedFor))
    val address = if (session.getRemoteHostName.nonEmpty) {
      session.getRemoteHostName
    } else {
      session.getRemoteIpAddress
    }
    Network.address(forwardedFor, address)
    session.getRemoteIpAddress
  }
}

case object NanoServer {

  /** Request context type. */
  type Context = HttpContext[IHTTPSession]

  private final case class HttpResponse(
    body: Array[Byte],
    status: IStatus,
    context: Option[Context],
    properties: Map[String, String]
  )
}
