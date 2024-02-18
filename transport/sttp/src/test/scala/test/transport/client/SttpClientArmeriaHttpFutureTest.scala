package test.transport.client

import automorph.spi.{ClientTransport, EffectSystem}
import automorph.system.FutureSystem
import automorph.transport.HttpMethod
import automorph.transport.client.SttpClient
import org.scalacheck.Arbitrary
import sttp.client3.armeria.future.ArmeriaFutureBackend
import test.transport.{HttpClientTest, HttpContextGenerator}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

final class SttpClientArmeriaHttpFutureTest extends HttpClientTest {

  type Effect[T] = Future[T]
  type Context = SttpClient.Context

  override lazy val system: EffectSystem[Effect] = FutureSystem()

  override def run[T](effect: Effect[T]): T =
    await(effect)

  override def arbitraryContext: Arbitrary[Context] =
    HttpContextGenerator.arbitrary

  override def clientTransport(fixtureId: String): ClientTransport[Effect, ?] =
    SttpClient(system, ArmeriaFutureBackend(), url(fixtureId), HttpMethod.Post)
}
