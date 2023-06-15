package test.transport

import automorph.spi.ClientTransport
import automorph.transport.http.HttpMethod
import automorph.transport.http.client.HttpClient
import java.net.URI
import test.core.HttpClientServerTest

trait HttpServerTest extends HttpClientServerTest {

  override def clientTransport(fixtureId: Int): ClientTransport[Effect, ?] =
    HttpClient(system, url(fixtureId), HttpMethod.Post)

  def url(fixtureId: Int): URI =
    new URI(s"http://localhost:${port(fixtureId)}")

  override def integration: Boolean =
    true
}
