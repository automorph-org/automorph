package test.transport.client

import automorph.spi.{ClientTransport, EffectSystem}
import automorph.system.FutureSystem
import automorph.transport.HttpMethod
import automorph.transport.client.JettyClient
import org.scalacheck.Arbitrary
import test.transport.{HttpClientTest, HttpContextGenerator}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

final class JettyClientHttpFutureTest extends HttpClientTest {

  type Effect[T] = Future[T]
  type Context = JettyClient.Context

  override lazy val system: EffectSystem[Effect] = FutureSystem()

  override def run[T](effect: Effect[T]): T =
    await(effect)

  override def arbitraryContext: Arbitrary[Context] =
    HttpContextGenerator.arbitrary

  override def clientTransport(fixtureId: String, server: OptionalServer): ClientTransport[Effect, Context] = {
    System.setProperty("org.eclipse.jetty.LEVEL", "ERROR")
    JettyClient(system, url(fixtureId), HttpMethod.Post)
  }
}
