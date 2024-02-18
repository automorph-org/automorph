package test.transport.http

import automorph.spi.{ClientTransport, EffectSystem}
import automorph.system.FutureSystem
import automorph.transport.http.HttpMethod
import automorph.transport.http.client.JettyClient
import org.scalacheck.Arbitrary
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import test.transport.HttpClientTest

final class JettyClientHttpFutureTest extends HttpClientTest {

  type Effect[T] = Future[T]
  type Context = JettyClient.Context

  override lazy val system: EffectSystem[Effect] = FutureSystem()

  override def run[T](effect: Effect[T]): T =
    await(effect)

  override def arbitraryContext: Arbitrary[Context] =
    HttpContextGenerator.arbitrary

  override def clientTransport(fixtureId: String): ClientTransport[Effect, Context] = {
    System.setProperty("org.eclipse.jetty.LEVEL", "ERROR")
    JettyClient(system, url(fixtureId), HttpMethod.Post)
  }
}
