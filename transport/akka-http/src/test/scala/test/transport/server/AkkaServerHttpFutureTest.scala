package test.transport.server

import automorph.spi.{EffectSystem, EndpointTransport, ServerTransport}
import automorph.system.FutureSystem
import automorph.transport.endpoint.AkkaHttpEndpoint
import automorph.transport.server.AkkaServer
import org.scalacheck.Arbitrary
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import test.transport.{HttpContextGenerator, HttpServerTest}

final class AkkaServerHttpFutureTest extends HttpServerTest {

  type Effect[T] = Future[T]
  type Context = AkkaServer.Context

  override lazy val system: EffectSystem[Effect] = FutureSystem()

  override def run[T](effect: Effect[T]): T =
    await(effect)

  override def arbitraryContext: Arbitrary[Context] =
    HttpContextGenerator.arbitrary

  override def serverTransport(fixtureId: String): ServerTransport[Effect, Context] = {
    AkkaServer(system, port(fixtureId))
  }

  override def endpointTransport: EndpointTransport[Future, Context, ?] =
    AkkaHttpEndpoint(system)
}