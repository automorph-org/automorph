package automorph.transport.http.endpoint

import automorph.log.{LogProperties, Logging, MessageLog}
import automorph.spi.{EffectSystem, EndpointTransport, RequestHandler}
import automorph.transport.http.endpoint.UndertowHttpEndpoint.Context
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
  mapException: Throwable => Int = HttpContext.defaultExceptionToStatusCode,
  handler: RequestHandler[Effect, Context] = RequestHandler.dummy[Effect, Context],
) extends HttpHandler with Logging with EndpointTransport[Effect, Context, HttpHandler] {

  private val log = MessageLog(logger, Protocol.Http.name)
  private implicit val system: EffectSystem[Effect] = effectSystem

  override def adapter: HttpHandler =
    this

  override def withHandler(handler: RequestHandler[Effect, Context]): UndertowHttpEndpoint[Effect] =
    copy(handler = handler)

  override def handleRequest(exchange: HttpServerExchange): Unit = {
    // Log the request
    val requestId = Random.id
    lazy val requestProperties = getRequestProperties(exchange, requestId)
    log.receivingRequest(requestProperties)
    val receiveCallback = new Receiver.FullBytesCallback {

      override def handle(exchange: HttpServerExchange, message: Array[Byte]): Unit = {
        log.receivedRequest(requestProperties)
        val handlerRunnable = new Runnable {

          override def run(): Unit =
            // Process the request
            Try {
              val requestBody = message.toArray[Byte]
              val response = handler.processRequest(requestBody, getRequestContext(exchange), requestId)
              response.either.map(
                _.fold(
                  error => sendErrorResponse(error, exchange, requestId, requestProperties),
                  result => {
                    // Send the response
                    val responseBody = result.map(_.responseBody).getOrElse(Array.emptyByteArray)
                    val status = result.flatMap(_.exception).map(mapException).getOrElse(StatusCodes.OK)
                    sendResponse(responseBody, status, result.flatMap(_.context), exchange, requestId)
                  },
                )
              ).runAsync
            }.failed.foreach { error =>
              sendErrorResponse(error, exchange, requestId, requestProperties)
            }
        }
        if (exchange.isInIoThread) {
          exchange.dispatch(handlerRunnable)
          ()
        } else {
          handlerRunnable.run()
        }
      }
    }
    Try(
      exchange.getRequestReceiver.receiveFullBytes(receiveCallback)
    ).recover { case error =>
      sendErrorResponse(error, exchange, requestId, requestProperties)
    }.get
  }

  private def sendErrorResponse(
    error: Throwable,
    exchange: HttpServerExchange,
    requestId: String,
    requestProperties: => Map[String, String],
  ): Unit = {
    log.failedProcessRequest(error, requestProperties)
    val responseBody = error.description.toByteArray
    val statusCode = StatusCodes.INTERNAL_SERVER_ERROR
    sendResponse(responseBody, statusCode, None, exchange, requestId)
  }

  private def sendResponse(
    responseBody: Array[Byte],
    statusCode: Int,
    responseContext: Option[Context],
    exchange: HttpServerExchange,
    requestId: String,
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
      exchange.getResponseHeaders.put(Headers.CONTENT_TYPE, handler.mediaType)
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

  private def getRequestProperties(exchange: HttpServerExchange, requestId: String): Map[String, String] = {
    val query = Option(exchange.getQueryString).filter(_.nonEmpty).map("?" + _).getOrElse("")
    val url = s"${exchange.getRequestURI}$query"
    ListMap(
      LogProperties.requestId -> requestId,
      LogProperties.client -> clientAddress(exchange),
      "URL" -> url,
      "Method" -> exchange.getRequestMethod.toString,
    )
  }

  private def clientAddress(exchange: HttpServerExchange): String = {
    val forwardedFor = Option(exchange.getRequestHeaders.get(Headers.X_FORWARDED_FOR_STRING)).map(_.getFirst)
    val address = exchange.getSourceAddress.toString
    Network.address(forwardedFor, address)
  }
}

case object UndertowHttpEndpoint {

  /** Request context type. */
  type Context = HttpContext[Either[HttpServerExchange, WebSocketHttpExchange]]
}
