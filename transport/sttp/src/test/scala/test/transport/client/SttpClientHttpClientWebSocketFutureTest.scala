package test.transport.client

import automorph.spi.{ClientTransport, EffectSystem}
import automorph.system.FutureSystem
import automorph.transport.HttpMethod
import automorph.transport.client.SttpClient
import org.scalacheck.Arbitrary
import sttp.client3.httpclient.HttpClientFutureBackend
import test.transport.{HttpContextGenerator, WebSocketClientTest}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

final class SttpClientHttpClientWebSocketFutureTest extends WebSocketClientTest {

  type Effect[T] = Future[T]
  type Context = SttpClient.Context

  override lazy val system: EffectSystem[Effect] = FutureSystem()

  override def run[T](effect: Effect[T]): T =
    await(effect)

  override def arbitraryContext: Arbitrary[Context] =
    HttpContextGenerator.arbitrary

  override def clientTransport(fixtureId: String): ClientTransport[Effect, ?] =
    SttpClient(system, HttpClientFutureBackend(), url(fixtureId), HttpMethod.Get)
}
