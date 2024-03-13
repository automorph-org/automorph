package test.transport.server

import automorph.spi.{EffectSystem, RequestHandler, ServerTransport}
import automorph.system.FutureSystem
import automorph.transport.server.TapirHttpEndpoint
import org.scalacheck.Arbitrary
import sttp.tapir.server.netty.{NettyFutureServer, NettyFutureServerBinding}
import test.transport.server.TapirNettyHttpFutureTest.TapirServer
import test.transport.{HttpContextGenerator, HttpServerTest}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

final class TapirNettyHttpFutureTest extends HttpServerTest {

  type Effect[T] = Future[T]
  type Context = TapirHttpEndpoint.Context

  override lazy val system: EffectSystem[Effect] = FutureSystem()
  override lazy val arbitraryContext: Arbitrary[Context] =
    HttpContextGenerator.arbitrary

  override def run[T](effect: Effect[T]): T =
    await(effect)

  def serverTransport(fixtureId: String): ServerTransport[Effect, Context, Unit] =
    TapirServer(system, port(fixtureId))
}

object TapirNettyHttpFutureTest {

  type Effect[T] = Future[T]
  type Context = TapirHttpEndpoint.Context

  final case class TapirServer(effectSystem: EffectSystem[Effect], port: Int)
    extends ServerTransport[Effect, Context, Unit] {
    private var rpcServer = TapirHttpEndpoint(effectSystem)
    private var server = Option.empty[NettyFutureServerBinding]

    override def endpoint: Unit =
      ()

    override def init(): Effect[Unit] =
      NettyFutureServer().port(port).addEndpoint(rpcServer.endpoint).start().map { activeServer =>
        server = Some(activeServer)
      }

    override def close(): Effect[Unit] =
      server.map { activeServer =>
        activeServer.stop()
      }.getOrElse(effectSystem.successful {})

    override def withHandler(handler: RequestHandler[Effect, Context]): ServerTransport[Effect, Context, Unit] = {
      rpcServer = rpcServer.withHandler(handler)
      this
    }
  }
}
