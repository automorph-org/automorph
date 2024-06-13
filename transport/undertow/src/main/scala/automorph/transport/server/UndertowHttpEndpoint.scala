package automorph.transport.server

import automorph.spi.{EffectSystem, RpcHandler, ServerTransport}
import automorph.transport.HttpContext.headerRpcNodeId
import automorph.transport.ServerHttpHandler.HttpMetadata
import automorph.transport.server.UndertowHttpEndpoint.{Context, HttpRequest, RequestCallback, requestQuery}
import automorph.transport.{ClientServerHttpHandler, HttpContext, HttpMethod, ServerHttpHandler, Protocol}
import automorph.util.Extensions.{ByteArrayOps, EffectOps}
import io.undertow.io.Receiver
import io.undertow.server.{HttpHandler, HttpServerExchange}
import io.undertow.util.{Headers, HttpString}
import io.undertow.websockets.spi.WebSocketHttpExchange
import java.io.IOException
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
  handler: RpcHandler[Effect, Context] = RpcHandler.dummy[Effect, Context],
) extends ServerTransport[Effect, Context, HttpHandler] {
  private lazy val httpHandler = new HttpHandler {

    override def handleRequest(exchange: HttpServerExchange): Unit =
      exchange.getRequestReceiver.receiveFullBytes(receiverCallback)
  }
  private val httpRequestHandler =
    ClientServerHttpHandler(receiveRequest, sendResponse, Protocol.Http, effectSystem, mapException, handler)
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

  override def requestHandler(handler: RpcHandler[Effect, Context]): UndertowHttpEndpoint[Effect] =
    copy(handler = handler)

  private def receiveRequest(request: HttpRequest): (Effect[Array[Byte]], HttpMetadata[Context]) = {
    val (exchange, body) = request
    val query = requestQuery(exchange.getQueryString)
    val requestMetadata = HttpMetadata(
      getRequestContext(exchange),
      httpRequestHandler.protocol,
      s"${exchange.getRequestURI}$query",
      Some(exchange.getRequestMethod.toString),
    )
    effectSystem.successful(body) -> requestMetadata
  }

  private def sendResponse(
    body: Array[Byte],
    metadata: HttpMetadata[Context],
    exchange: HttpServerExchange,
  ): Effect[Unit] = {
    if (!exchange.isResponseChannelAvailable) {
      throw new IOException("Response channel not available")
    }
    setResponseContext(exchange, metadata.context)
    exchange.getResponseHeaders.put(Headers.CONTENT_TYPE, metadata.contentType)
    exchange.setStatusCode(metadata.statusCodeOrOk)
    effectSystem.evaluate(exchange.getResponseSender.send(body.toByteBuffer))
  }

  private def getRequestContext(exchange: HttpServerExchange): Context = {
    val headers = exchange.getRequestHeaders.asScala.flatMap { headerValues =>
      headerValues.iterator.asScala.map(value => headerValues.getHeaderName.toString -> value)
    }.toSeq
    HttpContext(
      transportContext = Some(Left(exchange).withRight[WebSocketHttpExchange]),
      method = Some(HttpMethod.valueOf(exchange.getRequestMethod.toString)),
      headers = headers,
      peer = Some(client(exchange)),
    ).url(exchange.getRequestURI)
  }

  private def setResponseContext(exchange: HttpServerExchange, context: Context): Unit = {
    val responseHeaders = exchange.getResponseHeaders
    context.headers.foreach { case (name, value) =>
      responseHeaders.add(new HttpString(name), value)
    }
  }

  private def client(exchange: HttpServerExchange): String = {
    val forwardedFor = Option(exchange.getRequestHeaders.get(Headers.X_FORWARDED_FOR_STRING)).map(_.getFirst)
    val nodeId = Option(exchange.getRequestHeaders.get(headerRpcNodeId)).map(_.getFirst)
    val address = exchange.getSourceAddress.toString
    ServerHttpHandler.client(address, forwardedFor, nodeId)
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
    handler: ClientServerHttpHandler[Effect, Context, HttpRequest, HttpServerExchange],
    exchange: HttpServerExchange,
    requestBody: Array[Byte],
  ) extends Runnable {
    implicit private val system: EffectSystem[Effect] = effectSystem

    override def run(): Unit =
      handler.processRequest((exchange, requestBody), exchange).runAsync
  }
}
