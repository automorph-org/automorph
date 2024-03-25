package test.transport.client

import automorph.spi.{ClientTransport, EffectSystem}
import automorph.system.IdentitySystem
import automorph.system.IdentitySystem.Identity
import automorph.transport.HttpMethod
import automorph.transport.client.JettyClient
import org.scalacheck.Arbitrary
import test.transport.{HttpClientTest, HttpContextGenerator}

final class JettyClientHttpIdentityTest extends HttpClientTest {

  type Effect[T] = Identity[T]
  type Context = JettyClient.Context

  override lazy val system: EffectSystem[Effect] = IdentitySystem()

  override def run[T](effect: Effect[T]): T =
    effect

  override def arbitraryContext: Arbitrary[Context] =
    HttpContextGenerator.arbitrary

  override def clientTransport(fixtureId: String, server: OptionalServer): ClientTransport[Effect, Context] = {
    System.setProperty("org.eclipse.jetty.LEVEL", "ERROR")
    JettyClient(system, url(fixtureId), HttpMethod.Post)
  }
}
