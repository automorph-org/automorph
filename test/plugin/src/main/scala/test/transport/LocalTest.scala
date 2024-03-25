package test.transport

import automorph.spi.{ClientTransport, ServerTransport}
import automorph.transport.local.client.LocalClient
import org.scalacheck.{Arbitrary, Gen}
import test.core.ClientServerTest

trait LocalTest extends ClientServerTest {

  type Context = String

  private lazy val serverTransport = LocalServer[Effect, Context](system)

  override def arbitraryContext: Arbitrary[Context] =
    Arbitrary(Gen.asciiPrintableStr)

  override def clientTransport(fixtureId: String, server: OptionalServer): ClientTransport[Effect, ?] = {
    server.map(server => LocalClient[Effect, Context](system, arbitraryContext.arbitrary.sample.get, server)).getOrElse {
      throw new IllegalStateException("RPC server not defined")
    }
  }

  override def serverTransport(fixtureId: String): ServerTransport[Effect, Context, Unit] =
    serverTransport
}
