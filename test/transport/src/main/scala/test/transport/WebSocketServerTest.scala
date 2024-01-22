package test.transport

import automorph.spi.ClientTransport
import automorph.transport.http.HttpMethod
import automorph.transport.http.client.HttpClient
import java.net.URI
import test.core.ClientServerTest

trait WebSocketServerTest extends ClientServerTest {

  override def clientTransport(fixtureId: String): ClientTransport[Effect, ?] =
    HttpClient(system, url(fixtureId), HttpMethod.Post)

  def url(fixtureId: String): URI =
    new URI(s"ws://localhost:${port(fixtureId)}")
}
