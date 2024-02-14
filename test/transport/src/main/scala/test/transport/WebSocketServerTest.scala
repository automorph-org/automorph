package test.transport

import automorph.spi.ClientTransport
import automorph.transport.http.HttpMethod
import automorph.transport.http.client.HttpClient
import test.core.ClientServerTest
import java.net.URI

trait WebSocketServerTest extends ClientServerTest {

  override def clientTransport(fixtureId: String): ClientTransport[Effect, ?] =
    HttpClient(system, url(fixtureId), HttpMethod.Post)

  def url(fixtureId: String): URI =
    new URI(s"ws://localhost:${port(fixtureId)}")
}
