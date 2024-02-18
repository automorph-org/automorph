package test.transport.server

import automorph.spi.{EffectSystem, EndpointTransport, ServerTransport}
import automorph.system.FutureSystem
import automorph.transport.endpoint.VertxHttpEndpoint
import automorph.transport.server.VertxServer
import org.scalacheck.Arbitrary
import test.transport.{HttpContextGenerator, HttpServerTest}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

final class VertxServerHttpFutureTest extends HttpServerTest {

  type Effect[T] = Future[T]
  type Context = VertxServer.Context

  override lazy val system: EffectSystem[Effect] = FutureSystem()

  override def run[T](effect: Effect[T]): T =
    await(effect)

  override def arbitraryContext: Arbitrary[Context] =
    HttpContextGenerator.arbitrary

  override def serverTransport(fixtureId: String): ServerTransport[Effect, Context] =
    VertxServer(system, port(fixtureId))

  override def endpointTransport: EndpointTransport[Future, Context, ?] =
    VertxHttpEndpoint(system)
}
