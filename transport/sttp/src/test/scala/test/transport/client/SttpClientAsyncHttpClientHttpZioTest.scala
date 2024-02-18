package test.transport.client

import automorph.spi.ClientTransport
import automorph.system.ZioSystem
import automorph.transport.HttpMethod
import automorph.transport.client.SttpClient
import org.scalacheck.Arbitrary
import sttp.client3.asynchttpclient.zio.AsyncHttpClientZioBackend
import test.transport.{HttpClientTest, HttpContextGenerator}
import zio.{Runtime, Task, Unsafe}

final class SttpClientAsyncHttpClientHttpZioTest extends HttpClientTest {

  type Effect[T] = Task[T]
  type Context = SttpClient.Context

  override lazy val system: ZioSystem[Throwable] = {
    implicit val runtime: Runtime[Any] = Runtime.default
    ZioSystem()
  }

  override def run[T](effect: Effect[T]): T =
    Unsafe.unsafe { implicit unsafe =>
      system.runtime.unsafe.run(effect).getOrThrow()
    }

  override def arbitraryContext: Arbitrary[Context] =
    HttpContextGenerator.arbitrary

  override def clientTransport(fixtureId: String): ClientTransport[Effect, ?] =
    SttpClient(system, run(AsyncHttpClientZioBackend()), url(fixtureId), HttpMethod.Post)
}
