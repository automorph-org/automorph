package automorph.transport.websocket.endpoint

import automorph.spi.EffectSystem.Completable
import automorph.spi.{EffectSystem, RequestHandler, ServerTransport}
import automorph.transport.HttpRequestHandler.{RequestData, ResponseData, headerNodeId}
import automorph.transport.server.UndertowHttpEndpoint.requestQuery
import automorph.transport.websocket.endpoint.UndertowWebSocketEndpoint.{ConnectionListener, Context, ResponseCallback}
import automorph.transport.{HttpContext, HttpMethod, HttpRequestHandler, Protocol}
import automorph.util.Extensions.{ByteArrayOps, ByteBufferOps, EffectOps, StringOps}
import io.undertow.server.{HttpHandler, HttpServerExchange}
import io.undertow.util.Headers
import io.undertow.websockets.core.{AbstractReceiveListener, BufferedBinaryMessage, BufferedTextMessage, WebSocketCallback, WebSocketChannel, WebSockets}
import io.undertow.websockets.spi.WebSocketHttpExchange
import io.undertow.websockets.{WebSocketConnectionCallback, WebSocketProtocolHandshakeHandler}
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
) extends ServerTransport[Effect, Context, WebSocketConnectionCallback] {

  private lazy val webSocketConnectionCallback = new WebSocketConnectionCallback {

    override def onConnect(exchange: WebSocketHttpExchange, channel: WebSocketChannel): Unit = {
      val receiveListener = ConnectionListener[Effect](effectSystem, webSocketHandler, exchange)
      channel.getReceiveSetter.set(receiveListener)
      channel.resumeReceives()
    }
  }
  private val webSocketHandler =
    HttpRequestHandler(receiveRequest, sendResponse, Protocol.WebSocket, effectSystem, _ => 0, handler)
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

  override def requestHandler(handler: RequestHandler[Effect, Context]): UndertowWebSocketEndpoint[Effect] =
    copy(handler = handler)

  private def receiveRequest(
    request: (WebSocketHttpExchange, Array[Byte])
  ): (RequestData[Context], Effect[Array[Byte]]) = {
    val (exchange, body) = request
    val query = requestQuery(exchange.getQueryString)
    val requestData = RequestData(
      getRequestContext(exchange),
      webSocketHandler.protocol,
      s"${exchange.getRequestURI}$query",
      Some(HttpMethod.Get.name),
    )
    val requestBody = effectSystem.successful(body)
    (requestData, requestBody)
  }

  private def sendResponse(responseData: ResponseData[Context], channel: WebSocketChannel): Effect[Unit] =
    effectSystem.completable[Unit].flatMap { completable =>
      val responseCallback = ResponseCallback(completable, effectSystem)
      WebSockets.sendBinary(responseData.body.toByteBuffer, channel, responseCallback, ())
      completable.effect
    }

  private def getRequestContext(exchange: WebSocketHttpExchange): Context = {
    val headers = exchange.getRequestHeaders.asScala.view.mapValues(_.asScala).flatMap { case (name, values) =>
      values.map(value => name -> value)
    }.toSeq
    HttpContext(
      transportContext = Some(Right(exchange).withLeft[HttpServerExchange]),
      headers = headers,
      peerId = Some(clientId(exchange)),
    )
      .url(exchange.getRequestURI)
  }

  private def clientId(exchange: WebSocketHttpExchange): String = {
    val address = exchange.getPeerConnections.iterator().next().getSourceAddress.toString
    val forwardedFor = Option(exchange.getRequestHeaders.get(Headers.X_FORWARDED_FOR_STRING)).map(_.get(0))
    val nodeId = Option(exchange.getRequestHeaders.get(headerNodeId)).map(_.get(0))
    HttpRequestHandler.clientId(address, forwardedFor, nodeId)
  }
}

object UndertowWebSocketEndpoint {

  /** Request context type. */
  type Context = HttpContext[Either[HttpServerExchange, WebSocketHttpExchange]]

  final private case class ConnectionListener[Effect[_]](
    effectSystem: EffectSystem[Effect],
    handler: HttpRequestHandler[Effect, Context, (WebSocketHttpExchange, Array[Byte]), Unit, WebSocketChannel],
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
