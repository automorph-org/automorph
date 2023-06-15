package test.transport.websocket

import automorph.spi.{EffectSystem, EndpointTransport, ServerTransport}
import automorph.system.FutureSystem
import automorph.transport.http.server.UndertowServer
import automorph.transport.websocket.endpoint.UndertowWebSocketEndpoint
import org.scalacheck.Arbitrary
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import test.transport.WebSocketServerTest
import test.transport.http.HttpContextGenerator

class UndertowServerWebSocketFutureTest extends WebSocketServerTest {

  type Effect[T] = Future[T]
  type Context = UndertowServer.Context

  override lazy val system: EffectSystem[Effect] = FutureSystem()

  override def run[T](effect: Effect[T]): T =
    await(effect)

  override def arbitraryContext: Arbitrary[Context] =
    HttpContextGenerator.arbitrary

  override def serverTransport(fixtureId: Int): ServerTransport[Effect, Context] =
    UndertowServer[Effect](system, port(fixtureId))

  override def endpointTransport: EndpointTransport[Future, Context, ?] =
    UndertowWebSocketEndpoint(system)
}
