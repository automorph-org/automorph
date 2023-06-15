package test.transport.http

import automorph.spi.{ClientTransport, ServerTransport}
import automorph.transport.http.HttpMethod
import automorph.transport.http.client.HttpClient
import automorph.transport.http.server.NanoServer
import java.net.URI
import org.scalacheck.Arbitrary
import test.core.HttpClientServerTest

trait HttpClientHttpTest extends HttpClientServerTest {

  type Context = NanoServer.Context

  override def arbitraryContext: Arbitrary[Context] =
    HttpContextGenerator.arbitrary

  override def clientTransport(fixtureId: Int): ClientTransport[Effect, ?] =
    HttpClient(system, url(fixtureId), HttpMethod.Post)

  override def serverTransport(fixtureId: Int): ServerTransport[Effect, Context] =
    NanoServer(system, port(fixtureId))

  private def url(fixtureId: Int): URI =
    new URI(s"http://localhost:${port(fixtureId)}")
}
