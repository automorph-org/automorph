package test.transport

import automorph.spi.ServerTransport
import automorph.transport.server.NanoServer
import test.core.HttpClientServerTest
import java.net.URI

trait HttpClientTest extends HttpClientServerTest {

  override def serverTransport(fixtureId: String): ServerTransport[Effect, Context] =
    NanoServer[Effect](system, port(fixtureId)).asInstanceOf[ServerTransport[Effect, Context]]

  def url(fixtureId: String): URI =
    new URI(s"http://localhost:${port(fixtureId)}")
}
