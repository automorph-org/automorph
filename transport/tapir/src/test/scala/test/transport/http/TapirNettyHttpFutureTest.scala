package test.transport.http

import automorph.spi.{EffectSystem, RequestHandler, ServerTransport}
import automorph.system.FutureSystem
import automorph.transport.http.endpoint.TapirHttpEndpoint
import java.net.InetSocketAddress
import org.scalacheck.Arbitrary
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import sttp.tapir.server.netty.{NettyFutureServer, NettyFutureServerBinding}
import test.transport.HttpServerTest
import test.transport.http.TapirNettyHttpFutureTest.TapirServer

class TapirNettyHttpFutureTest extends HttpServerTest {

  type Effect[T] = Future[T]
  type Context = TapirHttpEndpoint.Context

  override lazy val system: EffectSystem[Effect] = FutureSystem()

  override def run[T](effect: Effect[T]): T =
    await(effect)

  override lazy val arbitraryContext: Arbitrary[Context] =
    HttpContextGenerator.arbitrary

  def serverTransport(fixtureId: Int): ServerTransport[Effect, Context] =
    TapirServer(system, port(fixtureId))

  override def integration: Boolean =
    true
}

case object TapirNettyHttpFutureTest {

  type Effect[T] = Future[T]
  type Context = TapirHttpEndpoint.Context

  final case class TapirServer(effectSystem: EffectSystem[Effect], port: Int) extends ServerTransport[Effect, Context] {
    private var endpoint = TapirHttpEndpoint(effectSystem)
    private var server = Option.empty[NettyFutureServerBinding[InetSocketAddress]]

    override def withHandler(handler: RequestHandler[Effect, Context]): ServerTransport[Effect, Context] = {
      endpoint = endpoint.withHandler(handler)
      this
    }

    override def init(): Effect[Unit] = {
      NettyFutureServer().port(port).addEndpoint(endpoint.adapter).start().map { activeServer =>
        server = Some(activeServer)
      }
    }

    override def close(): Effect[Unit] = {
      server.map { activeServer =>
        activeServer.stop()
      }.getOrElse(effectSystem.successful {})
    }
  }
}
