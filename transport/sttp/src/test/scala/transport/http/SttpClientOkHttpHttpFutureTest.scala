package transport.http

import automorph.spi.{ClientTransport, EffectSystem}
import automorph.system.FutureSystem
import automorph.transport.http.HttpMethod
import automorph.transport.http.client.SttpClient
import org.scalacheck.Arbitrary
import sttp.client3.okhttp.OkHttpFutureBackend
import test.transport.HttpClientTest
import test.transport.http.HttpContextGenerator
import scala.concurrent.Future

class SttpClientOkHttpHttpFutureTest extends HttpClientTest {

  type Effect[T] = Future[T]
  type Context = SttpClient.Context

  override lazy val system: EffectSystem[Effect] = FutureSystem()

  override def run[T](effect: Effect[T]): T =
    await(effect)

  override def arbitraryContext: Arbitrary[Context] =
    HttpContextGenerator.arbitrary

  override def clientTransport(fixtureId: String): ClientTransport[Effect, ?] =
    SttpClient(system, OkHttpFutureBackend(), url(fixtureId), HttpMethod.Post)
}
