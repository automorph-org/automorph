package transport.websocket

import automorph.spi.{ClientTransport, EffectSystem}
import automorph.system.FutureSystem
import automorph.transport.http.HttpMethod
import automorph.transport.http.client.SttpClient
import org.scalacheck.Arbitrary
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import sttp.client3.httpclient.HttpClientFutureBackend
import test.transport.WebSocketClientTest
import test.transport.http.HttpContextGenerator

class SttpClientHttpClientWebSocketFutureTest extends WebSocketClientTest {

  type Effect[T] = Future[T]
  type Context = SttpClient.Context

  override lazy val system: EffectSystem[Effect] = FutureSystem()

  override def run[T](effect: Effect[T]): T =
    await(effect)

  override def arbitraryContext: Arbitrary[Context] =
    HttpContextGenerator.arbitrary

  override def clientTransport(fixtureId: Int): ClientTransport[Effect, ?] =
    SttpClient(system, HttpClientFutureBackend(), url(fixtureId), HttpMethod.Get)
}
