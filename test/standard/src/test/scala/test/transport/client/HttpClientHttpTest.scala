package test.transport.client

import automorph.spi.{ClientTransport, ServerTransport}
import automorph.transport.HttpMethod
import automorph.transport.client.HttpClient
import automorph.transport.server.NanoServer
import org.scalacheck.Arbitrary
import test.core.HttpClientServerTest
import test.transport.HttpContextGenerator
import java.net.URI

trait HttpClientHttpTest extends HttpClientServerTest {

  type Context = NanoServer.Context

  override def arbitraryContext: Arbitrary[Context] =
    HttpContextGenerator.arbitrary

  override def clientTransport(fixtureId: String, server: OptionalServer): ClientTransport[Effect, ?] =
    HttpClient(system, url(fixtureId), HttpMethod.Post)

  override def serverTransport(fixtureId: String): ServerTransport[Effect, Context, Unit] =
    NanoServer(system, port(fixtureId))

  override def mandatory: Boolean =
    true

  private def url(fixtureId: String): URI =
    new URI(s"http://localhost:${port(fixtureId)}")
}
