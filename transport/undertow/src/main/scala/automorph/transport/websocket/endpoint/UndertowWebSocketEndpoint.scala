package automorph.transport.websocket.endpoint

import automorph.log.{LogProperties, Logging, MessageLog}
import automorph.spi.{EffectSystem, RequestHandler, ServerTransport}
import automorph.transport.HttpRequestHandler.{RequestData, ResponseData}
import automorph.transport.server.UndertowHttpEndpoint.requestQuery
import automorph.transport.websocket.endpoint.UndertowWebSocketEndpoint.{
  ConnectionListener, Context, ResponseCallback, WebSocketRequest,
}
import automorph.transport.{HttpContext, HttpRequestHandler, Protocol}
import automorph.util.Extensions.{ByteArrayOps, ByteBufferOps, EffectOps, StringOps}
import automorph.util.Network
import io.undertow.server.{HttpHandler, HttpServerExchange}
import io.undertow.util.{Headers, Methods}
import io.undertow.websockets.core.{
  AbstractReceiveListener, BufferedBinaryMessage, BufferedTextMessage, WebSocketCallback, WebSocketChannel, WebSockets,
}
import io.undertow.websockets.spi.WebSocketHttpExchange
import io.undertow.websockets.{WebSocketConnectionCallback, WebSocketProtocolHandshakeHandler}
import scala.collection.immutable.ListMap
import scala.jdk.CollectionConverters.{ListHasAsScala, MapHasAsScala}

/**
 * Undertow WebSocket endpoint transport plugin.
 *
 * Interprets WebSocket request message as an RPC request and processes it using the specified RPC request handler.
 *   - The response returned by the RPC request handler is used as WebSocket response message.
 *
 * @see
 *   [[https://en.wikipedia.org/wiki/WebSocket Transport protocol]]
 * @see
 *   [[https://undertow.io Library documentation]]
 * @see
 *   [[https://www.javadoc.io/doc/io.undertow/undertow-core/latest/index.html API]]
 * @constructor
 *   Creates an Undertow Websocket endpoint message transport plugin with specified effect system and request handler.
 * @param effectSystem
 *   effect system plugin
 * @param handler
 *   RPC request handler
 * @tparam Effect
 *   effect type
 */
final case class UndertowWebSocketEndpoint[Effect[_]](
  effectSystem: EffectSystem[Effect],
  handler: RequestHandler[Effect, Context] = RequestHandler.dummy[Effect, Context],
) extends ServerTransport[Effect, Context, WebSocketConnectionCallback] with Logging {

  private lazy val webSocketConnectionCallback = new WebSocketConnectionCallback {

    override def onConnect(exchange: WebSocketHttpExchange, channel: WebSocketChannel): Unit = {
      val receiveListener = ConnectionListener[Effect](effectSystem, webSocketHandler, exchange)
      channel.getReceiveSetter.set(receiveListener)
      channel.resumeReceives()
    }
  }
  private val webSocketHandler =
    HttpRequestHandler(receiveRequest, sendResponse, Protocol.WebSocket, effectSystem, _ => 0, handler, logger)
  private val log = MessageLog(logger, Protocol.WebSocket.name)

  /**
   * Creates an Undertow WebSocket handshake HTTP handler for this Undertow WebSocket callback.
   *
   * @param next
   *   Undertow handler invoked if a HTTP request does not contain a WebSocket handshake
   */
  def httpHandler(next: HttpHandler): WebSocketProtocolHandshakeHandler =
    new WebSocketProtocolHandshakeHandler(adapter, next)

  override def adapter: WebSocketConnectionCallback =
    webSocketConnectionCallback

  override def init(): Effect[Unit] =
    effectSystem.successful {}

  override def close(): Effect[Unit] =
    effectSystem.successful {}

  override def requestHandler(handler: RequestHandler[Effect, Context]): UndertowWebSocketEndpoint[Effect] =
    copy(handler = handler)

  private def receiveRequest(request: WebSocketRequest): RequestData[Context] = {
    val (exchange, requestBody) = request
    val query = requestQuery(exchange.getQueryString)
    RequestData(
      () => requestBody,
      getRequestContext(exchange),
      Protocol.Http,
      s"${exchange.getRequestURI}$query",
      clientAddress(exchange),
      Some(Methods.GET.toString),
    )
  }

  private def sendResponse(responseData: ResponseData[Context], channel: WebSocketChannel): Unit =
    WebSockets.sendBinary(
      responseData.body.toByteBuffer,
      channel,
      ResponseCallback(log, responseData.id, responseData.client),
      (),
    )

  private def getRequestContext(exchange: WebSocketHttpExchange): Context = {
    val headers = exchange.getRequestHeaders.asScala.view.mapValues(_.asScala).flatMap { case (name, values) =>
      values.map(value => name -> value)
    }.toSeq
    HttpContext(transportContext = Some(Right(exchange).withLeft[HttpServerExchange]), headers = headers)
      .url(exchange.getRequestURI)
  }

  private def clientAddress(exchange: WebSocketHttpExchange): String = {
    val forwardedFor = Option(exchange.getRequestHeaders.get(Headers.X_FORWARDED_FOR_STRING)).map(_.get(0))
    val address = exchange.getPeerConnections.iterator().next().getSourceAddress.toString
    Network.address(forwardedFor, address)
  }
}

object UndertowWebSocketEndpoint {

  /** Request context type. */
  type Context = HttpContext[Either[HttpServerExchange, WebSocketHttpExchange]]

  private type WebSocketRequest = (WebSocketHttpExchange, Array[Byte])

  final private case class ConnectionListener[Effect[_]](
    effectSystem: EffectSystem[Effect],
    handler: HttpRequestHandler[Effect, Context, WebSocketRequest, Unit, WebSocketChannel],
    exchange: WebSocketHttpExchange,
  ) extends AbstractReceiveListener {
    implicit private val system: EffectSystem[Effect] = effectSystem

    override def onFullTextMessage(channel: WebSocketChannel, message: BufferedTextMessage): Unit =
      handler.processRequest((exchange, message.getData.toByteArray), channel).runAsync

    @scala.annotation.nowarn("msg=deprecated")
    override def onFullBinaryMessage(channel: WebSocketChannel, message: BufferedBinaryMessage): Unit = {
      val data = message.getData
      handler.processRequest((exchange, WebSockets.mergeBuffers(data.getResource*).toByteArray), channel).either.map(
        _ => data.discard()
      ).runAsync
    }
  }

  final private case class ResponseCallback(
    log: MessageLog,
    requestId: String,
    client: String,
  ) extends WebSocketCallback[Unit] {
    private val responseProperties = ListMap(
      LogProperties.requestId -> requestId,
      LogProperties.client -> client,
    )

    override def complete(channel: WebSocketChannel, context: Unit): Unit =
      log.sentResponse(responseProperties)

    override def onError(channel: WebSocketChannel, context: Unit, error: Throwable): Unit =
      log.failedSendResponse(error, responseProperties)
  }
}
