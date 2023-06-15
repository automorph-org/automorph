package automorph.transport.websocket.endpoint

import automorph.log.{LogProperties, Logging, MessageLog}
import automorph.spi.{EffectSystem, EndpointTransport, RequestHandler}
import automorph.transport.http.{HttpContext, Protocol}
import automorph.transport.websocket.endpoint.UndertowWebSocketEndpoint.{ConnectionListener, Context}
import automorph.util.Extensions.{ByteArrayOps, ByteBufferOps, EffectOps, StringOps, ThrowableOps}
import automorph.util.{Network, Random}
import io.undertow.server.{HttpHandler, HttpServerExchange}
import io.undertow.util.Headers
import io.undertow.websockets.core.{
  AbstractReceiveListener, BufferedBinaryMessage, BufferedTextMessage, WebSocketCallback, WebSocketChannel, WebSockets
}
import io.undertow.websockets.spi.WebSocketHttpExchange
import io.undertow.websockets.{WebSocketConnectionCallback, WebSocketProtocolHandshakeHandler}
import scala.collection.immutable.ListMap
import scala.jdk.CollectionConverters.{ListHasAsScala, MapHasAsScala}
import scala.util.Try

/**
 * Undertow WebSocket endpoint transport plugin.
 *
 * Interprets WebSocket request message as an RPC request and processes it using the specified RPC request handler.
 * - The response returned by the RPC request handler is used as WebSocket response message.
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
) extends WebSocketConnectionCallback
  with AutoCloseable
  with Logging
  with EndpointTransport[Effect, Context, WebSocketConnectionCallback] {

  private val log = MessageLog(logger, Protocol.WebSocket.name)

  /**
   * Creates an Undertow WebSocket handshake HTTP handler for this Undertow WebSocket callback.
   *
   * @param next
   *   Undertow handler invoked if a HTTP request does not contain a WebSocket handshake
   */
  def handshakeHandler(next: HttpHandler): WebSocketProtocolHandshakeHandler =
    new WebSocketProtocolHandshakeHandler(adapter, next)

  override def adapter: WebSocketConnectionCallback =
    this

  override def withHandler(handler: RequestHandler[Effect, Context]): UndertowWebSocketEndpoint[Effect] =
    copy(handler = handler)

  override def onConnect(exchange: WebSocketHttpExchange, channel: WebSocketChannel): Unit = {
    val receiveListener = ConnectionListener(effectSystem, handler, log, exchange)
    channel.getReceiveSetter.set(receiveListener)
    channel.resumeReceives()
  }

  override def close(): Unit =
    ()
}

case object UndertowWebSocketEndpoint {

  /** Request context type. */
  type Context = HttpContext[Either[HttpServerExchange, WebSocketHttpExchange]]

  private final case class ConnectionListener[Effect[_]](
    effectSystem: EffectSystem[Effect],
    handler: RequestHandler[Effect, Context] = RequestHandler.dummy[Effect, Context],
    log: MessageLog,
    exchange: WebSocketHttpExchange,
  ) extends AbstractReceiveListener {

    private implicit val system: EffectSystem[Effect] = effectSystem

    override def onFullTextMessage(channel: WebSocketChannel, message: BufferedTextMessage): Unit =
      handle(exchange, message.getData.toByteArray, channel, () => ())

    override def onFullBinaryMessage(channel: WebSocketChannel, message: BufferedBinaryMessage): Unit = {
      val data = message.getData
      val requestBody = WebSockets.mergeBuffers(data.getResource*)
      handle(exchange, requestBody.toByteArray, channel, () => data.discard())
    }

    private def handle(
      exchange: WebSocketHttpExchange,
      requestBody: Array[Byte],
      channel: WebSocketChannel,
      discardMessage: () => Unit,
    ): Unit = {
      // Log the request
      val requestId = Random.id
      lazy val requestProperties = getRequestProperties(exchange, requestId)
      log.receivedRequest(requestProperties)

      // Process the request
      Try {
        val response = handler.processRequest(requestBody, getRequestContext(exchange), requestId)
        response.either.map(
          _.fold(
            error => sendErrorResponse(error, exchange, channel, requestId, requestProperties),
            result => {
              // Send the response
              val responseBody = result.map(_.responseBody).getOrElse(Array.emptyByteArray)
              sendResponse(responseBody, exchange, channel, requestId)
              discardMessage()
            },
          )
        ).runAsync
      }.failed.foreach { error =>
        sendErrorResponse(error, exchange, channel, requestId, requestProperties)
      }
    }

    private def sendErrorResponse(
      error: Throwable,
      exchange: WebSocketHttpExchange,
      channel: WebSocketChannel,
      requestId: String,
      requestProperties: => Map[String, String],
    ): Unit = {
      log.failedProcessRequest(error, requestProperties)
      val responseBody = error.description.toByteArray
      sendResponse(responseBody, exchange, channel, requestId)
    }

    private def sendResponse(
      message: Array[Byte],
      exchange: WebSocketHttpExchange,
      channel: WebSocketChannel,
      requestId: String,
    ): Unit = {
      // Log the response
      lazy val responseProperties = ListMap(
        LogProperties.requestId -> requestId,
        LogProperties.client -> clientAddress(exchange)
      )
      log.sendingResponse(responseProperties)

      // Send the response
      WebSockets.sendBinary(message.toByteBuffer, channel, ResponseCallback(log, responseProperties), ())
    }

    private def getRequestContext(exchange: WebSocketHttpExchange): Context = {
      val headers = exchange.getRequestHeaders.asScala.view.mapValues(_.asScala).flatMap { case (name, values) =>
        values.map(value => name -> value)
      }.toSeq
      HttpContext(transportContext = Some(Right(exchange).withLeft[HttpServerExchange]), headers = headers)
        .url(exchange.getRequestURI)
    }

    private def getRequestProperties(exchange: WebSocketHttpExchange, requestId: String): Map[String, String] = {
      val query = Option(exchange.getQueryString).filter(_.nonEmpty).map("?" + _).getOrElse("")
      val url = s"${exchange.getRequestURI}$query"
      Map(LogProperties.requestId -> requestId, LogProperties.client -> clientAddress(exchange), "URL" -> url)
    }

    private def clientAddress(exchange: WebSocketHttpExchange): String = {
      val forwardedFor = Option(exchange.getRequestHeaders.get(Headers.X_FORWARDED_FOR_STRING)).map(_.get(0))
      val address = exchange.getPeerConnections.iterator().next().getSourceAddress.toString
      Network.address(forwardedFor, address)
    }
  }

  private final case class ResponseCallback(
    log: MessageLog,
    responseProperties: ListMap[String, String],
  ) extends WebSocketCallback[Unit] {

    override def complete(channel: WebSocketChannel, context: Unit): Unit =
      log.sentResponse(responseProperties)

    override def onError(channel: WebSocketChannel, context: Unit, error: Throwable): Unit =
      log.failedSendResponse(error, responseProperties)
  }
}
