package test.transport.server

import automorph.spi.{EffectSystem, RequestHandler, ServerTransport}
import automorph.system.CatsEffectSystem
import automorph.transport.server.TapirHttpEndpoint
import cats.effect.IO
import cats.effect.unsafe.implicits.global
import com.comcast.ip4s.Port
import org.http4s.ember.server.EmberServerBuilder
import org.scalacheck.Arbitrary
import sttp.tapir.server.http4s.Http4sServerInterpreter
import test.transport.server.TapirHttp4sHttpCatsEffectTest.TapirServer
import test.transport.{HttpContextGenerator, HttpServerTest}

final class TapirHttp4sHttpCatsEffectTest extends HttpServerTest {

  type Effect[T] = IO[T]
  type Context = TapirHttpEndpoint.Context

  override lazy val system: EffectSystem[Effect] = CatsEffectSystem()
  override lazy val arbitraryContext: Arbitrary[Context] =
    HttpContextGenerator.arbitrary

  override def run[T](effect: Effect[T]): T =
    effect.unsafeRunSync()

  def serverTransport(fixtureId: String): ServerTransport[Effect, Context, Unit] =
    TapirServer(system, port(fixtureId))
}

object TapirHttp4sHttpCatsEffectTest {

  type Effect[T] = IO[T]
  type Context = TapirHttpEndpoint.Context

  final case class TapirServer(effectSystem: EffectSystem[Effect], port: Int)
    extends ServerTransport[Effect, Context, Unit] {
    private var rpcServer = TapirHttpEndpoint(effectSystem)
    private var server = Option.empty[IO[Unit]]

    override def endpoint: Unit =
      ()

    override def init(): Effect[Unit] =
      effectSystem.evaluate {
        val service = Http4sServerInterpreter[IO]().toRoutes(rpcServer.endpoint).orNotFound
        val serverBuilder = EmberServerBuilder.default[IO].withPort(Port.fromInt(port).get).withHttpApp(service)
        server = Some(serverBuilder.build.allocated.unsafeRunSync()._2)
      }

    override def close(): Effect[Unit] =
      server.map { activeServer =>
        effectSystem.evaluate {
          activeServer.unsafeRunSync()
        }
      }.getOrElse(effectSystem.successful {})

    override def requestHandler(handler: RequestHandler[Effect, Context]): ServerTransport[Effect, Context, Unit] = {
      rpcServer = rpcServer.requestHandler(handler)
      this
    }
  }
}
