package test.transport.server

import automorph.spi.{EffectSystem, EndpointTransport, ServerTransport}
import automorph.system.FutureSystem
import automorph.transport.endpoint.JettyWebSocketEndpoint
import automorph.transport.server.JettyServer
import org.scalacheck.Arbitrary
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import test.transport.{HttpContextGenerator, WebSocketServerTest}

final class JettyServerWebSocketFutureTest extends WebSocketServerTest {

  type Effect[T] = Future[T]
  type Context = JettyWebSocketEndpoint.Context

  override lazy val system: EffectSystem[Effect] = FutureSystem()

  override def run[T](effect: Effect[T]): T =
    await(effect)

  override def arbitraryContext: Arbitrary[Context] =
    HttpContextGenerator.arbitrary

  override def serverTransport(fixtureId: String): ServerTransport[Effect, Context] = {
    System.setProperty("org.eclipse.jetty.LEVEL", "ERROR")
    JettyServer(system, port(fixtureId))
  }

  override def endpointTransport: EndpointTransport[Future, Context, ?] =
    JettyWebSocketEndpoint(system)
}
