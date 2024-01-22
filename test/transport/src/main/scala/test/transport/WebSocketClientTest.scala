package test.transport

import automorph.spi.ServerTransport
import automorph.transport.http.server.NanoServer
import java.net.URI
import test.core.ClientServerTest

trait WebSocketClientTest extends ClientServerTest {

  override def serverTransport(fixtureId: String): ServerTransport[Effect, Context] =
    NanoServer[Effect](system, port(fixtureId)).asInstanceOf[ServerTransport[Effect, Context]]

  def url(fixtureId: String): URI =
    new URI(s"ws://localhost:${port(fixtureId)}")
}
