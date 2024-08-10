package test.transport.websocket.server

import automorph.spi.{EffectSystem, ServerTransport}
import automorph.system.FutureSystem
import automorph.transport.server.VertxServer
import org.scalacheck.Arbitrary
import test.transport.{HttpContextGenerator, WebSocketServerTest}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

final class VertxServerWebSocketFutureTest extends WebSocketServerTest {

  type Effect[T] = Future[T]
  type Context = VertxServer.Context

  override lazy val system: EffectSystem[Effect] = FutureSystem()

  override def run[T](effect: Effect[T]): T =
    await(effect)

  override def arbitraryContext: Arbitrary[Context] =
    HttpContextGenerator.arbitrary

  override def serverTransport(fixtureId: String): ServerTransport[Effect, Context, Unit] =
    VertxServer(system, port(fixtureId))
}