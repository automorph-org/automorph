package test.transport.local

import automorph.spi.{ClientTransport, ServerTransport}
import automorph.transport.local.LocalContext
import automorph.transport.local.client.LocalClient
import org.scalacheck.{Arbitrary, Gen}
import test.core.ClientServerTest

trait LocalTest extends ClientServerTest {

  type Context = LocalClient.Context

  private lazy val server = LocalServer(system, arbitraryContext.arbitrary.sample.get)

  override def arbitraryContext: Arbitrary[Context] =
    Arbitrary(Gen.asciiPrintableStr.map(LocalContext.apply))

  override def clientTransport(fixtureId: Int): ClientTransport[Effect, ?] =
    LocalClient(system, arbitraryContext.arbitrary.sample.get, server.handler)

  override def serverTransport(fixtureId: Int): ServerTransport[Effect, Context] =
    server
}
