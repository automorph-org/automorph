package transport.http

import automorph.spi.{ClientTransport, EffectSystem}
import automorph.system.IdentitySystem
import automorph.system.IdentitySystem.Identity
import automorph.transport.http.HttpMethod
import automorph.transport.http.client.SttpClient
import org.scalacheck.Arbitrary
import sttp.client3.HttpURLConnectionBackend
import test.transport.HttpClientTest
import test.transport.http.HttpContextGenerator

class SttpClientHttpUrlConnectionHttpIdentityTest extends HttpClientTest {

  type Effect[T] = Identity[T]
  type Context = SttpClient.Context

  override lazy val system: EffectSystem[Effect] = IdentitySystem()

  override def run[T](effect: Effect[T]): T =
    effect

  override def arbitraryContext: Arbitrary[Context] =
    HttpContextGenerator.arbitrary

  override def clientTransport(fixtureId: Int): ClientTransport[Effect, ?] =
    SttpClient.http(system, HttpURLConnectionBackend(), url(fixtureId), HttpMethod.Post)
}
