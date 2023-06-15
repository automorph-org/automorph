package automorph.transport.websocket.endpoint

import automorph.log.{LogProperties, Logging, MessageLog}
import automorph.spi.{EffectSystem, EndpointTransport, RequestHandler}
import automorph.transport.http.{HttpContext, Protocol}
import automorph.transport.websocket.endpoint.VertxWebSocketEndpoint.Context
import automorph.util.Extensions.{EffectOps, StringOps, ThrowableOps}
import automorph.util.{Network, Random}
import io.vertx.core.Handler
import io.vertx.core.buffer.Buffer
import io.vertx.core.http.{HttpServerRequest, ServerWebSocket}
import scala.collection.immutable.ListMap
import scala.jdk.CollectionConverters.ListHasAsScala
import scala.util.Try

/**
 * Vert.x WebSocket endpoint message transport plugin.
 *
 * Interprets WebSocket request message as an RPC request and processes it using the specified RPC request handler.
 * - The response returned by the RPC request handler is used as WebSocket response message.
 *
 * @see
 *   [[https://en.wikipedia.org/wiki/WebSocket Transport protocol]]
 * @see
 *   [[https://vertx.io Library documentation]]
 * @see
 *   [[https://vertx.io/docs/apidocs/index.html API]]
 * @constructor
 *   Creates a Vert.x Websocket endpoint message transport plugin with specified effect system and request handler.
 * @param effectSystem
 *   effect system plugin
 * @param handler
 *   RPC request handler
 * @tparam Effect
 *   effect type
 */
final case class VertxWebSocketEndpoint[Effect[_]](
  effectSystem: EffectSystem[Effect],
  handler: RequestHandler[Effect, Context] = RequestHandler.dummy[Effect, Context],
) extends Handler[ServerWebSocket] with Logging with EndpointTransport[Effect, Context, Handler[ServerWebSocket]] {

  private val headerXForwardedFor = "X-Forwarded-For"
  private val log = MessageLog(logger, Protocol.WebSocket.name)
  private implicit val system: EffectSystem[Effect] = effectSystem

  override def adapter: Handler[ServerWebSocket] =
    this

  override def withHandler(handler: RequestHandler[Effect, Context]): VertxWebSocketEndpoint[Effect] =
    copy(handler = handler)

  override def handle(session: ServerWebSocket): Unit = {
    // Log the request
    val requestId = Random.id
    lazy val requestProperties = getRequestProperties(session, requestId)
    log.receivingRequest(requestProperties)
    session.binaryMessageHandler { buffer =>
      // Process the request
      Try {
        val requestBody = buffer.getBytes.toArray[Byte]
        log.receivedRequest(requestProperties)
        val handlerResult = handler.processRequest(requestBody, getRequestContext(session), requestId)
        handlerResult.either.map(
          _.fold(
            error => sendErrorResponse(error, session, requestId, requestProperties),
            result => {
              // Send the response
              val responseBody = result.map(_.responseBody).getOrElse(Array.emptyByteArray)
              sendResponse(responseBody, session, requestId)
            },
          )
        ).runAsync
      }.failed.foreach { error =>
        sendErrorResponse(error, session, requestId, requestProperties)
      }
    }
    ()
  }

  private def sendErrorResponse(
    error: Throwable,
    session: ServerWebSocket,
    requestId: String,
    requestProperties: => Map[String, String],
  ): Unit = {
    log.failedProcessRequest(error, requestProperties)
    val responseBody = error.description.toByteArray
    sendResponse(responseBody, session, requestId)
  }

  private def sendResponse(responseBody: Array[Byte], session: ServerWebSocket, requestId: String): Unit = {
    // Log the response
    lazy val responseProperties =
      ListMap(LogProperties.requestId -> requestId, LogProperties.client -> clientAddress(session))
    log.sendingResponse(responseProperties)

    // Send the response
    session.writeBinaryMessage(Buffer.buffer(responseBody)).onSuccess { _ =>
      log.sentResponse(responseProperties)
    }.onFailure { error =>
      log.failedSendResponse(error, responseProperties)
    }
    ()
  }

  private def clientAddress(request: ServerWebSocket): String = {
    val forwardedFor = Option(request.headers().get(headerXForwardedFor))
    val address = Option(request.remoteAddress.hostName).orElse(Option(request.remoteAddress.hostAddress)).getOrElse("")
    Network.address(forwardedFor, address)
  }

  private def getRequestContext(request: ServerWebSocket): Context = {
    val headers = request.headers.entries.asScala.map(entry => entry.getKey -> entry.getValue).toSeq
    HttpContext(transportContext = Some(Right(request).withLeft[HttpServerRequest]), headers = headers).url(request.uri)
  }

  private def getRequestProperties(request: ServerWebSocket, requestId: String): Map[String, String] =
    ListMap(LogProperties.requestId -> requestId, LogProperties.client -> clientAddress(request), "URL" -> request.uri)
}

case object VertxWebSocketEndpoint {

  /** Request context type. */
  type Context = HttpContext[Either[HttpServerRequest, ServerWebSocket]]
}
