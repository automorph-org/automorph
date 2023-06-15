package transport.http

import automorph.spi.ClientTransport
import automorph.system.ZioSystem
import automorph.transport.http.HttpMethod
import automorph.transport.http.client.SttpClient
import org.scalacheck.Arbitrary
import sttp.client3.asynchttpclient.zio.AsyncHttpClientZioBackend
import test.transport.HttpClientTest
import test.transport.http.HttpContextGenerator
import zio.{Task, Unsafe}

class SttpClientAsyncHttpClientHttpZioTest extends HttpClientTest {

  type Effect[T] = Task[T]
  type Context = SttpClient.Context

  override lazy val system: ZioSystem[Any] = ZioSystem.default

  override def run[T](effect: Effect[T]): T =
    Unsafe.unsafe { implicit unsafe =>
      system.runtime.unsafe.run(effect).getOrThrow()
    }

  override def arbitraryContext: Arbitrary[Context] =
    HttpContextGenerator.arbitrary

  override def clientTransport(id: Int): ClientTransport[Effect, ?] =
    SttpClient.http(system, run(AsyncHttpClientZioBackend()), url(id), HttpMethod.Post)
}
