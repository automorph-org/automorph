package test.transport

import automorph.spi.ServerTransport
import automorph.transport.http.server.NanoServer
import java.net.URI
import test.core.HttpClientServerTest

trait HttpClientTest extends HttpClientServerTest {

  override def serverTransport(fixtureId: Int): ServerTransport[Effect, Context] =
    NanoServer[Effect](system, port(fixtureId)).asInstanceOf[ServerTransport[Effect, Context]]

  def url(fixtureId: Int): URI =
    new URI(s"http://localhost:${port(fixtureId)}")

  override def integration: Boolean =
    true
}
