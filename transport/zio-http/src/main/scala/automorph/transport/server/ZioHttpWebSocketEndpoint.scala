package automorph.transport.server

import automorph.log.Logging
import automorph.spi.{EffectSystem, RpcHandler, ServerTransport}
import automorph.transport.ServerHttpHandler.HttpMetadata
import automorph.transport.server.ZioHttpWebSocketEndpoint.Context
import automorph.transport.{ClientServerHttpHandler, HttpContext, Protocol}
import zio.http.ChannelEvent.{ExceptionCaught, Read}
import zio.http.{WebSocketChannel, WebSocketFrame}
import zio.{Chunk, IO}
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
  handler: RpcHandler[({ type Effect[A] = IO[Fault, A] })#Effect, Context] =
    RpcHandler.dummy[({ type Effect[A] = IO[Fault, A] })#Effect, Context],
) extends ServerTransport[({ type Effect[A] = IO[Fault, A] })#Effect, Context, WebSocketChannel => IO[Throwable, Any]]
  with Logging {
  private val webSocketHandler =
    ClientServerHttpHandler(receiveRequest, sendResponse, Protocol.WebSocket, effectSystem, _ => 0, handler)

  override def adapter: WebSocketChannel => IO[Throwable, Any] =
    channel => handle(channel)

  override def init(): IO[Fault, Unit] =
    effectSystem.successful {}

  override def close(): IO[Fault, Unit] =
    effectSystem.successful {}

  override def requestHandler(
    handler: RpcHandler[({ type Effect[A] = IO[Fault, A] })#Effect, Context]
  ): ZioHttpWebSocketEndpoint[Fault] =
    copy(handler = handler)

  private def handle(channel: WebSocketChannel): IO[Throwable, Unit] =
    webSocketHandler.processRequest(channel, channel).mapError { _ =>
      new RuntimeException(s"Error processing ${Protocol.WebSocket.name} request")
    }

  private def receiveRequest(channel: WebSocketChannel): (IO[Fault, Array[Byte]], HttpMetadata[Context]) = {
    val requestMetadata = HttpMetadata(HttpContext[Unit](), webSocketHandler.protocol, "", None)
    val requestBody = effectSystem.completable[Array[Byte]].flatMap { completable =>
      channel.receiveAll {
        case Read(WebSocketFrame.Binary(request)) => completable.succeed(request.toArray)
        case ExceptionCaught(cause) => completable.fail(cause)
        case _ => completable.fail(
            new IllegalStateException(s"Error processing ${Protocol.WebSocket.name} request")
          )
      }
      completable.effect
    }
    requestBody -> requestMetadata
  }

  private def sendResponse(
    body: Array[Byte],
    @unused metadata: HttpMetadata[Context],
    channel: WebSocketChannel,
  ): IO[Fault, Unit] =
    channel.send(Read(WebSocketFrame.Binary(Chunk.fromArray(body)))).foldZIO(
      error => effectSystem.failed(error),
      _ => effectSystem.successful {},
    )
}

object ZioHttpWebSocketEndpoint {

  /** Request context type. */
  type Context = HttpContext[Unit]
}
