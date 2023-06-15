package test.transport.http

import automorph.spi.{EffectSystem, EndpointTransport, ServerTransport}
import automorph.system.FutureSystem
import automorph.transport.http.endpoint.AkkaHttpEndpoint
import automorph.transport.http.server.AkkaServer
import org.scalacheck.Arbitrary
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import test.transport.HttpServerTest

class AkkaServerHttpFutureTest extends HttpServerTest {

  type Effect[T] = Future[T]
  type Context = AkkaServer.Context

  override lazy val system: EffectSystem[Effect] = FutureSystem()

  override def run[T](effect: Effect[T]): T =
    await(effect)

  override def arbitraryContext: Arbitrary[Context] =
    HttpContextGenerator.arbitrary

  override def serverTransport(fixtureId: Int): ServerTransport[Effect, Context] = {
    AkkaServer(system, port(fixtureId))
  }

  override def endpointTransport: EndpointTransport[Future, Context, ?] =
    AkkaHttpEndpoint(system)
}
