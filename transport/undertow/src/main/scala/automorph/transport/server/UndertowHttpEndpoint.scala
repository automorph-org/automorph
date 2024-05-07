package automorph.transport.server

import automorph.log.Logging
import automorph.spi.{EffectSystem, RequestHandler, ServerTransport}
import automorph.transport.HttpRequestHandler.{RequestData, ResponseData}
import automorph.transport.server.UndertowHttpEndpoint.{Context, HttpRequest, RequestCallback, requestQuery}
import automorph.transport.{HttpContext, HttpMethod, HttpRequestHandler, Protocol}
import automorph.util.Extensions.{ByteArrayOps, EffectOps}
import automorph.util.Network
import io.undertow.io.Receiver
import io.undertow.server.{HttpHandler, HttpServerExchange}
import io.undertow.util.{Headers, HttpString}
import io.undertow.websockets.spi.WebSocketHttpExchange
import java.io.IOException
import scala.annotation.unused
import scala.jdk.CollectionConverters.{IterableHasAsScala, IteratorHasAsScala}

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
) extends ServerTransport[Effect, Context, HttpHandler] with Logging {
  private lazy val httpHandler = new HttpHandler {

    override def handleRequest(exchange: HttpServerExchange): Unit =
      exchange.getRequestReceiver.receiveFullBytes(receiverCallback)
  }
  private val httpRequestHandler =
    HttpRequestHandler(receiveRequest, sendResponse, Protocol.Http, effectSystem, mapException, handler, logger)
  private val receiverCallback = new Receiver.FullBytesCallback {

    override def handle(exchange: HttpServerExchange, requestBody: Array[Byte]): Unit = {
      val handlerRunnable = RequestCallback(effectSystem, httpRequestHandler, exchange, requestBody)
      if (exchange.isInIoThread) {
        exchange.dispatch(handlerRunnable)
        ()
      } else handlerRunnable.run()
    }
  }

  override def adapter: HttpHandler =
    httpHandler

  override def init(): Effect[Unit] =
    effectSystem.successful {}

  override def close(): Effect[Unit] =
    effectSystem.successful {}

  override def requestHandler(handler: RequestHandler[Effect, Context]): UndertowHttpEndpoint[Effect] =
    copy(handler = handler)

  private def receiveRequest(request: HttpRequest): RequestData[Context] = {
    val (exchange, requestBody) = request
    val query = requestQuery(exchange.getQueryString)
    RequestData(
      () => requestBody,
      getRequestContext(exchange),
      httpRequestHandler.protocol,
      s"${exchange.getRequestURI}$query",
      clientAddress(exchange),
      Some(exchange.getRequestMethod.toString),
    )
  }

  private def sendResponse(
    responseData: ResponseData[Context],
    exchange: HttpServerExchange,
    @unused logResponse: Option[Throwable] => Unit,
  ): Effect[Unit] = {
    if (!exchange.isResponseChannelAvailable) {
      throw new IOException("Response channel not available")
    }
    setResponseContext(exchange, responseData.context)
    exchange.getResponseHeaders.put(Headers.CONTENT_TYPE, responseData.contentType)
    exchange.setStatusCode(responseData.statusCode)
    effectSystem.evaluate(exchange.getResponseSender.send(responseData.body.toByteBuffer))
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

  private def setResponseContext(exchange: HttpServerExchange, context: Option[Context]): Unit = {
    val responseHeaders = exchange.getResponseHeaders
    context.toSeq.flatMap(_.headers).foreach { case (name, value) =>
      responseHeaders.add(new HttpString(name), value)
    }
  }

  private def clientAddress(exchange: HttpServerExchange): String = {
    val forwardedFor = Option(exchange.getRequestHeaders.get(Headers.X_FORWARDED_FOR_STRING)).map(_.getFirst)
    val address = exchange.getSourceAddress.toString
    Network.address(forwardedFor, address)
  }
}

object UndertowHttpEndpoint {

  /** Request context type. */
  type Context = HttpContext[Either[HttpServerExchange, WebSocketHttpExchange]]

  private type HttpRequest = (HttpServerExchange, Array[Byte])

  private[automorph] def requestQuery(query: String): String =
    Option(query).filter(_.nonEmpty).map("?" + _).getOrElse("")

  final private case class RequestCallback[Effect[_]](
    effectSystem: EffectSystem[Effect],
    handler: HttpRequestHandler[Effect, Context, HttpRequest, Unit, HttpServerExchange],
    exchange: HttpServerExchange,
    requestBody: Array[Byte],
  ) extends Runnable {
    implicit private val system: EffectSystem[Effect] = effectSystem

    override def run(): Unit =
      handler.processRequest((exchange, requestBody), exchange).runAsync
  }
}
