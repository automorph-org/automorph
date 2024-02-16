package automorph.transport.http.endpoint

import automorph.log.{LogProperties, Logging, MessageLog}
import automorph.spi.{EffectSystem, EndpointTransport, RequestHandler}
import automorph.transport.http.Protocol
import automorph.transport.http.endpoint.ZioHttpWebSocketEndpoint.Context
import automorph.util.Extensions.{StringOps, ThrowableOps, TryOps}
import automorph.util.Random
import zio.http.ChannelEvent.{ExceptionCaught, Read}
import zio.http.{WebSocketChannel, WebSocketFrame}
import zio.{Chunk, Trace, ZIO}
import scala.collection.immutable.ListMap
import scala.util.Try

/**
 * ZIO HTTP WebSocket endpoint message transport plugin.
 *
 * Interprets WebSocket request body as a RPC request and processes it with the specified RPC handler.
 *   - The response returned by the RPC handler is used as WebSocket response body.
 *
 * @see
 *   [[https://en.wikipedia.org/wiki/WebSocket Transport protocol]]
 * @see
 *   [[https://zio.dev/zio-http Library documentation]]
 * @see
 *   [[https://javadoc.io/doc/dev.zio/zio-http_3/latest/index.html API]]
 * @constructor
 *   Creates an ZIO HTTP WebSocket endpoint message transport plugin with specified effect system and request handler.
 * @param effectSystem
 *   effect system plugin
 * @param handler
 *   RPC request handler
 * @tparam Environment
 *   ZIO environment type
 * @tparam Fault
 *   ZIO error type
 */
final case class ZioHttpWebSocketEndpoint[Environment, Fault](
  effectSystem: EffectSystem[({ type Effect[A] = ZIO[Environment, Fault, A] })#Effect],
  handler: RequestHandler[({ type Effect[A] = ZIO[Environment, Fault, A] })#Effect, Context] =
    RequestHandler.dummy[({ type Effect[A] = ZIO[Environment, Fault, A] })#Effect, Context],
) extends Logging
  with EndpointTransport[
    ({ type Effect[A] = ZIO[Environment, Fault, A] })#Effect,
    Context,
    WebSocketChannel => ZIO[Environment, Throwable, Any],
  ] {
  private val log = MessageLog(logger, Protocol.WebSocket.name)

  override def adapter: WebSocketChannel => ZIO[Environment, Throwable, Any] =
    channel => handle(channel)

  override def withHandler(
    handler: RequestHandler[({ type Effect[A] = ZIO[Environment, Fault, A] })#Effect, Context]
  ): ZioHttpWebSocketEndpoint[Environment, Fault] =
    copy(handler = handler)

  private def handle(channel: WebSocketChannel): ZIO[Environment, Throwable, Any] =
    channel.receiveAll {
      case Read(WebSocketFrame.Binary(request)) =>
        // Log the request
        val requestId = Random.id
        lazy val requestProperties = getRequestProperties(requestId)
        log.receivedRequest(requestProperties)

        // Process the request
        Try {
          val requestBody = request.toArray
          val handlerResult = handler.processRequest(requestBody, (), requestId)
          handlerResult.fold(
            _ => {
              val message = s"Failed to process ${Protocol.WebSocket.name} request"
              createErrorResponse(message, implicitly[Trace], requestId, requestProperties)
            },
            result => {
              // Create the response
              val responseBody = result.map(_.responseBody).getOrElse(Array.emptyByteArray)
              createResponse(responseBody, requestId)
            },
          )
        }.foldError { error =>
          ZIO.succeed(createErrorResponse(error, requestId, requestProperties))
        }.flatMap(frame => channel.send(Read(frame)))
      case ExceptionCaught(cause) => ZIO.attempt(log.failedProcessRequest(cause, Map()))
      case _ => ZIO.unit
    }

  private def createErrorResponse(
    error: Throwable,
    requestId: String,
    requestProperties: => Map[String, String],
  ): WebSocketFrame = {
    log.failedProcessRequest(error, requestProperties)
    val responseBody = error.trace.mkString("\n").toByteArray
    createResponse(responseBody, requestId)
  }

  private def createErrorResponse(
    message: String,
    trace: Trace,
    requestId: String,
    requestProperties: => Map[String, String],
  ): WebSocketFrame = {
    logger.error(s"$message\n$trace", requestProperties)
    val responseBody = trace.toString.toByteArray
    createResponse(responseBody, requestId)
  }

  private def createResponse(responseBody: Array[Byte], requestId: String): WebSocketFrame = {
    // Log the response
    lazy val responseProperties = ListMap(LogProperties.requestId -> requestId)

    // Create the response
    val response = WebSocketFrame.Binary(Chunk.fromArray(responseBody))
    log.sendingResponse(responseProperties)
    response
  }

  private def getRequestProperties(requestId: String): Map[String, String] =
    ListMap(LogProperties.requestId -> requestId)
}

object ZioHttpWebSocketEndpoint {

  /** Request context type. */
  type Context = Unit
}
