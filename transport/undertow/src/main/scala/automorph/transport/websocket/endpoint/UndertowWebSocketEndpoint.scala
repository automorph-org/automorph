package automorph.transport.websocket.endpoint

import automorph.spi.EffectSystem.Completable
import automorph.spi.{EffectSystem, RpcHandler, ServerTransport}
import automorph.transport.HttpContext.headerRpcNodeId
import automorph.transport.ServerHttpHandler.HttpMetadata
import automorph.transport.server.UndertowHttpEndpoint.requestQuery
import automorph.transport.websocket.endpoint.UndertowWebSocketEndpoint.{ConnectionListener, Context, ResponseCallback}
import automorph.transport.{ClientServerHttpHandler, HttpContext, ServerHttpHandler, Protocol}
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
 * @param handler
 *   RPC request handler
 * @tparam Effect
 *   effect type
 */
final case class UndertowWebSocketEndpoint[Effect[_]](
  effectSystem: EffectSystem[Effect],
  handler: RpcHandler[Effect, Context] = RpcHandler.dummy[Effect, Context],
) extends ServerTransport[Effect, Context, WebSocketConnectionCallback] {

  private lazy val webSocketConnectionCallback = new WebSocketConnectionCallback {

    override def onConnect(exchange: WebSocketHttpExchange, channel: WebSocketChannel): Unit = {
      val receiveListener = ConnectionListener[Effect](effectSystem, webSocketHandler, exchange)
      channel.getReceiveSetter.set(receiveListener)
      channel.resumeReceives()
    }
  }
  private val webSocketHandler =
    ClientServerHttpHandler(receiveRequest, sendResponse, Protocol.WebSocket, effectSystem, _ => 0, handler)
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
    copy(handler = handler)

  private def receiveRequest(
    request: (WebSocketHttpExchange, Array[Byte])
  ): (Effect[Array[Byte]], HttpMetadata[Context]) = {
    val (exchange, body) = request
    val query = requestQuery(exchange.getQueryString)
    val requestMetadata = HttpMetadata(
      getRequestContext(exchange),
      webSocketHandler.protocol,
      s"${exchange.getRequestURI}$query",
      None,
    )
    effectSystem.successful(body) -> requestMetadata
  }

  private def sendResponse(
    body: Array[Byte],
    @unused metadata: HttpMetadata[Context],
    channel: WebSocketChannel,
  ): Effect[Unit] =
    effectSystem.completable[Unit].flatMap { completable =>
      val responseCallback = ResponseCallback(completable, effectSystem)
      WebSockets.sendBinary(body.toByteBuffer, channel, responseCallback, ())
      completable.effect
    }

  private def getRequestContext(exchange: WebSocketHttpExchange): Context = {
    val headers = exchange.getRequestHeaders.asScala.view.mapValues(_.asScala).flatMap { case (name, values) =>
      values.map(value => name -> value)
    }.toSeq
    HttpContext(
      transportContext = Some(Right(exchange).withLeft[HttpServerExchange]),
      headers = headers,
      peer = Some(clientId(exchange)),
    )
      .url(exchange.getRequestURI)
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

  final private case class ConnectionListener[Effect[_]](
    effectSystem: EffectSystem[Effect],
    handler: ClientServerHttpHandler[Effect, Context, (WebSocketHttpExchange, Array[Byte]), WebSocketChannel],
    exchange: WebSocketHttpExchange,
  ) extends AbstractReceiveListener {
    implicit private val system: EffectSystem[Effect] = effectSystem

    override def onFullTextMessage(channel: WebSocketChannel, message: BufferedTextMessage): Unit =
      handler.processRequest((exchange, message.getData.toByteArray), channel).runAsync

    @scala.annotation.nowarn("msg=deprecated")
    override def onFullBinaryMessage(channel: WebSocketChannel, message: BufferedBinaryMessage): Unit = {
      val data = message.getData
      handler.processRequest((exchange, WebSockets.mergeBuffers(data.getResource*).toByteArray), channel).either.map(
        _ => data.free()
      ).runAsync
    }
  }

  final private case class ResponseCallback[Effect[_]](
    completable: Completable[Effect, Unit],
    effectSystem: EffectSystem[Effect],
  ) extends WebSocketCallback[Unit] {
    implicit private val system: EffectSystem[Effect] = effectSystem

    override def complete(channel: WebSocketChannel, context: Unit): Unit =
      completable.succeed(()).runAsync

    override def onError(channel: WebSocketChannel, context: Unit, error: Throwable): Unit =
      completable.fail(error).runAsync
  }
}
