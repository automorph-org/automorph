package automorph.transport.server

import automorph.log.Logging
import automorph.spi.{EffectSystem, RequestHandler, ServerTransport}
import automorph.transport.HttpRequestHandler.{RequestData, ResponseData}
import automorph.transport.server.ZioHttpWebSocketEndpoint.Context
import automorph.transport.{HttpContext, HttpRequestHandler, Protocol}
import zio.http.ChannelEvent.{ExceptionCaught, Read}
import zio.http.{WebSocketChannel, WebSocketFrame}
import zio.{Chunk, IO}

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
    HttpRequestHandler(receiveRequest, sendResponse, Protocol.WebSocket, effectSystem, _ => 0, handler, logger)

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
    webSocketHandler.processRequest(channel, channel).mapError { _ =>
      new RuntimeException(s"Error processing ${Protocol.WebSocket.name} request")
    }

  private def receiveRequest(channel: WebSocketChannel): (RequestData[Context], IO[Fault, Array[Byte]]) = {
    val requestData = RequestData(HttpContext[Unit](), webSocketHandler.protocol, "", "", None)
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
    (requestData, requestBody)
  }

  private def sendResponse(responseData: ResponseData[Context], channel: WebSocketChannel): IO[Fault, Unit] =
    channel.send(Read(WebSocketFrame.Binary(Chunk.fromArray(responseData.body)))).either.flatMap {
      case Left(error) => effectSystem.failed(error)
      case Right(()) => effectSystem.successful {}
    }
}

object ZioHttpWebSocketEndpoint {

  /** Request context type. */
  type Context = HttpContext[Unit]
}
