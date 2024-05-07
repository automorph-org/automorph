package automorph.transport.server

import automorph.log.Logging
import automorph.spi.{EffectSystem, RequestHandler, ServerTransport}
import automorph.transport.HttpRequestHandler.{RequestData, ResponseData}
import automorph.transport.server.ZioHttpWebSocketEndpoint.Context
import automorph.transport.{HttpContext, HttpRequestHandler, Protocol}
import zio.http.ChannelEvent.{ExceptionCaught, Read}
import zio.http.{WebSocketChannel, WebSocketFrame}
import zio.{Chunk, IO, ZIO}
import scala.annotation.unused

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
 * @tparam Fault
 *   ZIO error type
 */
final case class ZioHttpWebSocketEndpoint[Fault](
  effectSystem: EffectSystem[({ type Effect[A] = IO[Fault, A] })#Effect],
  handler: RequestHandler[({ type Effect[A] = IO[Fault, A] })#Effect, Context] =
    RequestHandler.dummy[({ type Effect[A] = IO[Fault, A] })#Effect, Context],
) extends ServerTransport[({ type Effect[A] = IO[Fault, A] })#Effect, Context, WebSocketChannel => IO[Throwable, Any]]
  with Logging {
  private val webSocketHandler =
    HttpRequestHandler(receiveRequest, createResponse, Protocol.WebSocket, effectSystem, _ => 0, handler, logger)

  override def adapter: WebSocketChannel => IO[Throwable, Any] =
    channel => handle(channel)

  override def init(): IO[Fault, Unit] =
    effectSystem.successful {}

  override def close(): IO[Fault, Unit] =
    effectSystem.successful {}

  override def requestHandler(
    handler: RequestHandler[({ type Effect[A] = IO[Fault, A] })#Effect, Context]
  ): ZioHttpWebSocketEndpoint[Fault] =
    copy(handler = handler)

  private def handle(channel: WebSocketChannel): IO[Throwable, Unit] =
    channel.receiveAll {
      case Read(WebSocketFrame.Binary(request)) =>
        webSocketHandler.processRequest(request, ()).mapError(_ => new RuntimeException()).flatMap { frame =>
          channel.send(Read(frame))
        }
      case ExceptionCaught(cause) =>
        // FIXME - check if the following is required to obtain error details: implicitly[Trace].toString.toByteArray
        webSocketHandler.processReceiveError(cause, receiveRequest(Chunk.empty), ())
          .mapError(_ => new RuntimeException(s"Error sending ${Protocol.WebSocket.name} response"))
          .flatMap { frame =>
            channel.send(Read(frame))
          }
      case _ => ZIO.unit
    }

  private def receiveRequest(body: Chunk[Byte]): RequestData[Context] =
    RequestData(() => body.toArray, HttpContext(), webSocketHandler.protocol, "", "", None)

  private def createResponse(responseData: ResponseData[Context], @unused session: Unit): IO[Fault, WebSocketFrame] =
    effectSystem.successful(WebSocketFrame.Binary(Chunk.fromArray(responseData.body)))
}

object ZioHttpWebSocketEndpoint {

  /** Request context type. */
  type Context = HttpContext[Unit]
}
