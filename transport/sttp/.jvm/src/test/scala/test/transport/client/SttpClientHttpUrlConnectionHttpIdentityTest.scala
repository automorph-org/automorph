package test.transport.client

import automorph.spi.{ClientTransport, EffectSystem}
import automorph.system.IdentitySystem
import automorph.system.IdentitySystem.Identity
import automorph.transport.HttpMethod
import automorph.transport.client.SttpClient
import org.scalacheck.Arbitrary
import sttp.client3.HttpURLConnectionBackend
import test.transport.{HttpClientTest, HttpContextGenerator}

final class SttpClientHttpUrlConnectionHttpIdentityTest extends HttpClientTest {

  type Effect[T] = Identity[T]
  type Context = SttpClient.Context

  override lazy val system: EffectSystem[Effect] = IdentitySystem()

  override def run[T](effect: Effect[T]): T =
    effect

  override def arbitraryContext: Arbitrary[Context] =
    HttpContextGenerator.arbitrary

  override def clientTransport(fixtureId: String, server: OptionalServer): ClientTransport[Effect, ?] =
    SttpClient(system, HttpURLConnectionBackend(), url(fixtureId), HttpMethod.Post)
}
