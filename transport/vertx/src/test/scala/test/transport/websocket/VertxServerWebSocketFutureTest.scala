package test.transport.websocket

import automorph.spi.{EffectSystem, EndpointTransport, ServerTransport}
import automorph.system.FutureSystem
import automorph.transport.http.server.VertxServer
import automorph.transport.websocket.endpoint.VertxWebSocketEndpoint
import org.scalacheck.Arbitrary
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import test.transport.WebSocketServerTest
import test.transport.http.HttpContextGenerator

class VertxServerWebSocketFutureTest extends WebSocketServerTest {

  type Effect[T] = Future[T]
  type Context = VertxServer.Context

  override lazy val system: EffectSystem[Effect] = FutureSystem()

  override def run[T](effect: Effect[T]): T =
    await(effect)

  override def arbitraryContext: Arbitrary[Context] =
    HttpContextGenerator.arbitrary

  override def serverTransport(fixtureId: Int): ServerTransport[Effect, Context] =
    VertxServer(system, port(fixtureId))

  override def endpointTransport: EndpointTransport[Future, Context, ?] =
    VertxWebSocketEndpoint(system)

  override def testServerClose: Boolean =
    false
}
