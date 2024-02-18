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

  override def clientTransport(fixtureId: String): ClientTransport[Effect, ?] =
    LocalClient(system, arbitraryContext.arbitrary.sample.get, serverTransport.handler)

  override def serverTransport(fixtureId: String): ServerTransport[Effect, Context] =
    serverTransport
}
