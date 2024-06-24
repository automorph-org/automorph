package automorph.transport.server

import automorph.spi.{EffectSystem, RpcHandler, ServerTransport}
import automorph.transport.HttpContext.headerRpcNodeId
import automorph.transport.server.UndertowHttpEndpoint.{Context, HttpRequest, RequestCallback, requestQuery}
import automorph.transport.{ClientServerHttpHandler, HttpContext, HttpMetadata, HttpMethod, Protocol, ServerHttpHandler}
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
 * @param rpcHandler
 *   RPC request handler
 * @tparam Effect
 *   effect type
 */
final case class UndertowHttpEndpoint[Effect[_]](
  effectSystem: EffectSystem[Effect],
  mapException: Throwable => Int = HttpContext.toStatusCode,
  rpcHandler: RpcHandler[Effect, Context] = RpcHandler.dummy[Effect, Context],
) extends ServerTransport[Effect, Context, HttpHandler] {
  private lazy val httpHandler = new HttpHandler {

    override def handleRequest(exchange: HttpServerExchange): Unit =
      exchange.getRequestReceiver.receiveFullBytes(receiverCallback)
  }
  private val handler =
    ClientServerHttpHandler(receiveRequest, sendResponse, Protocol.Http, effectSystem, mapException, rpcHandler)
  private val receiverCallback = new Receiver.FullBytesCallback {

    override def handle(exchange: HttpServerExchange, requestBody: Array[Byte]): Unit = {
      val handlerRunnable = RequestCallback(requestBody, exchange, effectSystem, handler)
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

  override def rpcHandler(handler: RpcHandler[Effect, Context]): UndertowHttpEndpoint[Effect] =
    copy(rpcHandler = handler)

  private def receiveRequest(request: HttpRequest): (Effect[Array[Byte]], Context) = {
    val (body, exchange) = request
    effectSystem.successful(body) -> getRequestContext(exchange)
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
    val query = requestQuery(exchange.getQueryString)
    HttpContext(
      transportContext = Some(Left(exchange).withRight[WebSocketHttpExchange]),
      method = Some(HttpMethod.valueOf(exchange.getRequestMethod.toString)),
      headers = headers,
      peer = Some(client(exchange)),
    ).url(s"${exchange.getRequestURI}$query")
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

  private type HttpRequest = (Array[Byte], HttpServerExchange)

  private[automorph] def requestQuery(query: String): String =
    Option(query).filter(_.nonEmpty).map("?" + _).getOrElse("")

  final private case class RequestCallback[Effect[_]](
    requestBody: Array[Byte],
    exchange: HttpServerExchange,
    effectSystem: EffectSystem[Effect],
    handler: ClientServerHttpHandler[Effect, Context, HttpRequest, HttpServerExchange],
  ) extends Runnable {
    implicit private val system: EffectSystem[Effect] = effectSystem

    override def run(): Unit =
      handler.processRequest((requestBody, exchange), exchange).runAsync
  }
}
