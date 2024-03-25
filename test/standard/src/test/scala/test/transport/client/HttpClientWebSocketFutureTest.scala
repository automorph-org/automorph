package test.transport.client

import automorph.spi.{ClientTransport, EffectSystem, ServerTransport}
import automorph.system.FutureSystem
import automorph.transport.HttpMethod
import automorph.transport.client.HttpClient
import automorph.transport.server.NanoServer
import java.net.URI
import org.scalacheck.Arbitrary
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import test.core.ClientServerTest
import test.transport.HttpContextGenerator

final class HttpClientWebSocketFutureTest extends ClientServerTest {

  type Effect[T] = Future[T]
  type Context = NanoServer.Context

  override lazy val system: EffectSystem[Effect] = FutureSystem()

  override def run[T](effect: Effect[T]): T =
    await(effect)

  override def arbitraryContext: Arbitrary[Context] =
    HttpContextGenerator.arbitrary

  override def clientTransport(fixtureId: String, server: OptionalServer): ClientTransport[Effect, ?] =
    HttpClient(system, url(fixtureId), HttpMethod.Get)

  override def serverTransport(fixtureId: String): ServerTransport[Effect, Context, Unit] =
    NanoServer[Effect](system, port(fixtureId))

  private def url(fixtureId: String): URI =
    new URI(s"ws://localhost:${port(fixtureId)}")

  override def basic: Boolean =
    true
}
