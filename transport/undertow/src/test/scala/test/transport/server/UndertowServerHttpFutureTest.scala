package test.transport.server

import automorph.spi.{EffectSystem, EndpointTransport, ServerTransport}
import automorph.system.FutureSystem
import automorph.transport.endpoint.UndertowHttpEndpoint
import automorph.transport.server.UndertowServer
import org.scalacheck.Arbitrary
import test.transport.{HttpContextGenerator, HttpServerTest}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

final class UndertowServerHttpFutureTest extends HttpServerTest {

  type Effect[T] = Future[T]
  type Context = UndertowServer.Context

  override lazy val system: EffectSystem[Effect] = FutureSystem()

  override def run[T](effect: Effect[T]): T =
    await(effect)

  override def arbitraryContext: Arbitrary[Context] =
    HttpContextGenerator.arbitrary

  override def serverTransport(fixtureId: String): ServerTransport[Effect, Context] =
    UndertowServer[Effect](system, port(fixtureId))

  override def endpointTransport: EndpointTransport[Future, Context, ?] =
    UndertowHttpEndpoint(system)
}
