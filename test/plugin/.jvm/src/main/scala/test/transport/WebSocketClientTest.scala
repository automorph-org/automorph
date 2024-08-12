package test.transport

import automorph.spi.ServerTransport
import automorph.transport.server.NanoServer
import test.core.ClientServerTest

import java.net.URI

trait WebSocketClientTest extends ClientServerTest {

  override def serverTransport(fixtureId: String): ServerTransport[Effect, Context, Unit] =
    NanoServer[Effect](system, port(fixtureId)).asInstanceOf[ServerTransport[Effect, Context, Unit]]

  def url(fixtureId: String): URI =
    new URI(s"ws://localhost:${port(fixtureId)}")
}
