package automorph.transport.http.endpoint

import automorph.log.{LogProperties, Logging, MessageLog}
import automorph.spi.{EffectSystem, EndpointTransport, RequestHandler}
import automorph.transport.http.endpoint.UndertowHttpEndpoint.{
  Context, ReceiverFullBytesCallback, clientAddress, requestQuery, sendErrorResponse,
}
import automorph.transport.http.{HttpContext, HttpMethod, Protocol}
import automorph.util.Extensions.{ByteArrayOps, EffectOps, StringOps, ThrowableOps, TryOps}
import automorph.util.{Network, Random}
import io.undertow.io.Receiver
import io.undertow.server.{HttpHandler, HttpServerExchange}
import io.undertow.util.{Headers, HttpString, StatusCodes}
import io.undertow.websockets.spi.WebSocketHttpExchange
import java.io.IOException
import scala.collection.immutable.ListMap
import scala.jdk.CollectionConverters.{IterableHasAsScala, IteratorHasAsScala}
import scala.util.Try

/**
 * Undertow HTTP endpoint transport plugin.
 *
 * Interprets HTTP request body as an RPC request and processes it using the specified RPC request handler.
 *   - The response returned by the RPC request handler is used as HTTP response body.
 *
 * @see
 *   [[https://en.wikipedia.org/wiki/HTTP Transport protocol]]
 * @see
 *   [[https://undertow.io Library documentation]]
 * @see
 *   [[https://www.javadoc.io/doc/io.undertow/undertow-core/latest/index.html API]]
 * @constructor
 *   Creates an Undertow HTTP endpoint message transport plugin with specified effect system and request handler.
 * @param effectSystem
 *   effect system plugin
 * @param mapException
 *   maps an exception to a corresponding HTTP status code
 * @param handler
 *   RPC request handler
 * @tparam Effect
 *   effect type
 */
final case class UndertowHttpEndpoint[Effect[_]](
  effectSystem: EffectSystem[Effect],
  mapException: Throwable => Int = HttpContext.toStatusCode,
  handler: RequestHandler[Effect, Context] = RequestHandler.dummy[Effect, Context],
) extends EndpointTransport[Effect, Context, HttpHandler] with Logging {

  private lazy val httpHandler = new HttpHandler {

    override def handleRequest(exchange: HttpServerExchange): Unit = {
      // Log the request
      val requestId = Random.id
      lazy val requestProperties = getRequestProperties(exchange, requestId)
      log.receivingRequest(requestProperties)
      val receiveCallback =
        ReceiverFullBytesCallback(effectSystem, mapException, handler, log, requestId, requestProperties)
      Try(
        exchange.getRequestReceiver.receiveFullBytes(receiveCallback)
      ).recover { case error =>
        sendErrorResponse(error, exchange, requestId, requestProperties, handler.mediaType, log)
      }.get
    }
  }
  private val log = MessageLog(logger, Protocol.Http.name)

  override def adapter: HttpHandler =
    httpHandler

  override def withHandler(handler: RequestHandler[Effect, Context]): UndertowHttpEndpoint[Effect] =
    copy(handler = handler)

  private def getRequestProperties(exchange: HttpServerExchange, requestId: String): Map[String, String] = {
    val query = requestQuery(exchange.getQueryString)
    val url = s"${exchange.getRequestURI}$query"
    ListMap(
      LogProperties.requestId -> requestId,
      LogProperties.client -> clientAddress(exchange),
      "URL" -> url,
      "Method" -> exchange.getRequestMethod.toString,
    )
  }
}

object UndertowHttpEndpoint {

  /** Request context type. */
  type Context = HttpContext[Either[HttpServerExchange, WebSocketHttpExchange]]

  private[automorph] def requestQuery(query: String) =
    Option(query).filter(_.nonEmpty).map("?" + _).getOrElse("")

  private def sendErrorResponse(
    error: Throwable,
    exchange: HttpServerExchange,
    requestId: String,
    requestProperties: => Map[String, String],
    mediaType: String,
    log: MessageLog,
  ): Unit = {
    log.failedProcessRequest(error, requestProperties)
    val responseBody = error.description.toByteArray
    val statusCode = StatusCodes.INTERNAL_SERVER_ERROR
    sendResponse(responseBody, statusCode, None, exchange, requestId, mediaType, log)
  }

  private def sendResponse(
    responseBody: Array[Byte],
    statusCode: Int,
    responseContext: Option[Context],
    exchange: HttpServerExchange,
    requestId: String,
    mediaType: String,
    log: MessageLog,
  ): Unit = {
    // Log the response
    val responseStatusCode = responseContext.flatMap(_.statusCode).getOrElse(statusCode)
    lazy val responseProperties = ListMap(
      LogProperties.requestId -> requestId,
      LogProperties.client -> clientAddress(exchange),
      "Status" -> responseStatusCode.toString,
    )
    log.sendingResponse(responseProperties)

    // Send the response
    Try {
      if (!exchange.isResponseChannelAvailable) {
        throw new IOException("Response channel not available")
      }
      setResponseContext(exchange, responseContext)
      exchange.getResponseHeaders.put(Headers.CONTENT_TYPE, mediaType)
      exchange.setStatusCode(responseStatusCode).getResponseSender.send(responseBody.toByteBuffer)
      log.sentResponse(responseProperties)
    }.onError(error => log.failedSendResponse(error, responseProperties)).get
  }

  private def setResponseContext(exchange: HttpServerExchange, responseContext: Option[Context]): Unit = {
    val responseHeaders = exchange.getResponseHeaders
    responseContext.toSeq.flatMap(_.headers).foreach { case (name, value) =>
      responseHeaders.add(new HttpString(name), value)
    }
  }

  private def clientAddress(exchange: HttpServerExchange): String = {
    val forwardedFor = Option(exchange.getRequestHeaders.get(Headers.X_FORWARDED_FOR_STRING)).map(_.getFirst)
    val address = exchange.getSourceAddress.toString
    Network.address(forwardedFor, address)
  }

  final private case class ReceiverFullBytesCallback[Effect[_]](
    effectSystem: EffectSystem[Effect],
    mapException: Throwable => Int,
    handler: RequestHandler[Effect, Context],
    log: MessageLog,
    requestId: String,
    requestProperties: Map[String, String],
  ) extends Receiver.FullBytesCallback {

    override def handle(exchange: HttpServerExchange, message: Array[Byte]): Unit = {
      log.receivedRequest(requestProperties)
      val handlerRunnable =
        HandlerRunnable(effectSystem, mapException, handler, log, exchange, message, requestId, requestProperties)
      if (exchange.isInIoThread) {
        exchange.dispatch(handlerRunnable)
        ()
      } else handlerRunnable.run()
    }
  }

  final private case class HandlerRunnable[Effect[_]](
    effectSystem: EffectSystem[Effect],
    mapException: Throwable => Int,
    handler: RequestHandler[Effect, Context],
    log: MessageLog,
    exchange: HttpServerExchange,
    message: Array[Byte],
    requestId: String,
    requestProperties: Map[String, String],
  ) extends Runnable {
    implicit private val system: EffectSystem[Effect] = effectSystem

    override def run(): Unit =
      // Process the request
      Try {
        val response = handler.processRequest(message, getRequestContext(exchange), requestId)
        response.either.map(
          _.fold(
            error => sendErrorResponse(error, exchange, requestId, requestProperties, handler.mediaType, log),
            result => {
              // Send the response
              val responseBody = result.map(_.responseBody).getOrElse(Array.emptyByteArray)
              val status = result.flatMap(_.exception).map(mapException).getOrElse(StatusCodes.OK)
              sendResponse(responseBody, status, result.flatMap(_.context), exchange, requestId, handler.mediaType, log)
            },
          )
        ).runAsync
      }.failed.foreach { error =>
        sendErrorResponse(error, exchange, requestId, requestProperties, handler.mediaType, log)
      }

    private def getRequestContext(exchange: HttpServerExchange): Context = {
      val headers = exchange.getRequestHeaders.asScala.flatMap { headerValues =>
        headerValues.iterator.asScala.map(value => headerValues.getHeaderName.toString -> value)
      }.toSeq
      HttpContext(
        transportContext = Some(Left(exchange).withRight[WebSocketHttpExchange]),
        method = Some(HttpMethod.valueOf(exchange.getRequestMethod.toString)),
        headers = headers,
      ).url(exchange.getRequestURI)
    }
  }
}
