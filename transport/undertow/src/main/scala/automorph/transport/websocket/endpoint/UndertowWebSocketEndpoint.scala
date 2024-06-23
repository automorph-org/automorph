package automorph.transport.websocket.endpoint

import automorph.spi.EffectSystem.Completable
import automorph.spi.{EffectSystem, RpcHandler, ServerTransport}
import automorph.transport.HttpContext.headerRpcNodeId
import automorph.transport.server.UndertowHttpEndpoint.requestQuery
import automorph.transport.websocket.endpoint.UndertowWebSocketEndpoint.{ConnectionListener, Context, ResponseCallback, WebSocketRequest}
import automorph.transport.{ClientServerHttpHandler, HttpContext, HttpMetadata, Protocol, ServerHttpHandler}
import automorph.util.Extensions.{ByteArrayOps, ByteBufferOps, EffectOps, StringOps}
import io.undertow.server.{HttpHandler, HttpServerExchange}
import io.undertow.util.Headers
import io.undertow.websockets.core.{AbstractReceiveListener, BufferedBinaryMessage, BufferedTextMessage, WebSocketCallback, WebSocketChannel, WebSockets}
import io.undertow.websockets.spi.WebSocketHttpExchange
import io.undertow.websockets.{WebSocketConnectionCallback, WebSocketProtocolHandshakeHandler}
import scala.annotation.unused
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
 * @param rpcHandler
 *   RPC request handler
 * @tparam Effect
 *   effect type
 */
final case class UndertowWebSocketEndpoint[Effect[_]](
  effectSystem: EffectSystem[Effect],
  rpcHandler: RpcHandler[Effect, Context] = RpcHandler.dummy[Effect, Context],
) extends ServerTransport[Effect, Context, WebSocketConnectionCallback] {

  private lazy val webSocketConnectionCallback = new WebSocketConnectionCallback {

    override def onConnect(exchange: WebSocketHttpExchange, channel: WebSocketChannel): Unit = {
      val receiveListener = ConnectionListener(exchange, effectSystem, handler)
      channel.getReceiveSetter.set(receiveListener)
      channel.resumeReceives()
    }
  }
  private val handler =
    ClientServerHttpHandler(receiveRequest, sendResponse, Protocol.WebSocket, effectSystem, _ => 0, rpcHandler)
  implicit private val system: EffectSystem[Effect] = effectSystem

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

  override def requestHandler(handler: RpcHandler[Effect, Context]): UndertowWebSocketEndpoint[Effect] =
    copy(rpcHandler = handler)

  private def receiveRequest(request: WebSocketRequest): (Effect[Array[Byte]], Context) = {
    val (body, exchange) = request
    effectSystem.successful(body) -> getRequestContext(exchange)
  }

  private def sendResponse(
    body: Array[Byte],
    @unused metadata: HttpMetadata[Context],
    channel: WebSocketChannel,
  ): Effect[Unit] =
    effectSystem.completable[Unit].flatMap { expectedResponseSent =>
      val responseCallback = ResponseCallback(expectedResponseSent, effectSystem, handler)
      WebSockets.sendBinary(body.toByteBuffer, channel, responseCallback, ())
      expectedResponseSent.effect
    }

  private def getRequestContext(exchange: WebSocketHttpExchange): Context = {
    val headers = exchange.getRequestHeaders.asScala.view.mapValues(_.asScala).flatMap { case (name, values) =>
      values.map(value => name -> value)
    }.toSeq
    val query = requestQuery(exchange.getQueryString)
    HttpContext(
      transportContext = Some(Right(exchange).withLeft[HttpServerExchange]),
      headers = headers,
      peer = Some(clientId(exchange)),
    ).url(s"${exchange.getRequestURI}$query")
  }

  private def clientId(exchange: WebSocketHttpExchange): String = {
    val address = exchange.getPeerConnections.iterator().next().getSourceAddress.toString
    val forwardedFor = Option(exchange.getRequestHeaders.get(Headers.X_FORWARDED_FOR_STRING)).map(_.get(0))
    val nodeId = Option(exchange.getRequestHeaders.get(headerRpcNodeId)).map(_.get(0))
    ServerHttpHandler.client(address, forwardedFor, nodeId)
  }
}

object UndertowWebSocketEndpoint {

  /** Request context type. */
  type Context = HttpContext[Either[HttpServerExchange, WebSocketHttpExchange]]

  private type WebSocketRequest = (Array[Byte], WebSocketHttpExchange)

  final private case class ConnectionListener[Effect[_]](
    exchange: WebSocketHttpExchange,
    effectSystem: EffectSystem[Effect],
    handler: ClientServerHttpHandler[Effect, Context, WebSocketRequest, WebSocketChannel],
  ) extends AbstractReceiveListener {
    implicit private val system: EffectSystem[Effect] = effectSystem

    @scala.annotation.nowarn("msg=deprecated")
    override def onFullBinaryMessage(channel: WebSocketChannel, message: BufferedBinaryMessage): Unit = {
      val data = message.getData
      handler.processRequest(WebSockets.mergeBuffers(data.getResource*).toByteArray -> exchange, channel).either.map(
        _ => data.free()
      ).runAsync
    }

    override def onFullTextMessage(channel: WebSocketChannel, message: BufferedTextMessage): Unit =
      handler.processRequest(message.getData.toByteArray -> exchange, channel).runAsync

    override def onError(channel: WebSocketChannel, error: Throwable): Unit = {
      handler.failedReceiveWebSocketRequest(error)
      super.onError(channel, error)
    }
  }

  final private case class ResponseCallback[Effect[_]](
    expectedResponseSent: Completable[Effect, Unit],
    effectSystem: EffectSystem[Effect],
    handler: ClientServerHttpHandler[Effect, Context, WebSocketRequest, WebSocketChannel],
  ) extends WebSocketCallback[Unit] {
    implicit private val system: EffectSystem[Effect] = effectSystem

    override def complete(channel: WebSocketChannel, context: Unit): Unit =
      expectedResponseSent.succeed(()).runAsync

    override def onError(channel: WebSocketChannel, context: Unit, error: Throwable): Unit =
      expectedResponseSent.fail(error).runAsync
  }
}
