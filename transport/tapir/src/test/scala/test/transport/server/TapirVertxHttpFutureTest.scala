package test.transport.server

import automorph.spi.{EffectSystem, RequestHandler, ServerTransport}
import automorph.system.FutureSystem
import automorph.transport.server.TapirHttpEndpoint
import io.vertx.core.Vertx
import io.vertx.core.http.HttpServer
import io.vertx.ext.web.Router
import org.scalacheck.Arbitrary
import sttp.tapir.server.vertx.VertxFutureServerInterpreter
import test.transport.server.TapirVertxHttpFutureTest.TapirServer
import test.transport.{HttpContextGenerator, HttpServerTest}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

final class TapirVertxHttpFutureTest extends HttpServerTest {

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

object TapirVertxHttpFutureTest {

  type Effect[T] = Future[T]
  type Context = TapirHttpEndpoint.Context

  final case class TapirServer(effectSystem: EffectSystem[Effect], port: Int)
    extends ServerTransport[Effect, Context, Unit] {
    private var rpcServer = TapirHttpEndpoint(effectSystem)
    private var server = Option.empty[HttpServer]

    override def endpoint: Unit =
      ()

    override def init(): Effect[Unit] =
      effectSystem.evaluate {
        server = server.map(_.listen(port).toCompletionStage.toCompletableFuture.get())
      }

    override def close(): Effect[Unit] =
      server.map { activeServer =>
        effectSystem.evaluate {
          activeServer.close().toCompletionStage.toCompletableFuture.get()
          ()
        }
      }.getOrElse(effectSystem.successful {})

    override def withHandler(handler: RequestHandler[Effect, Context]): ServerTransport[Effect, Context, Unit] = {
      rpcServer = rpcServer.withHandler(handler)
      val vertx = Vertx.vertx()
      val router = Router.router(vertx)
      VertxFutureServerInterpreter().route(rpcServer.endpoint)(router)
      server = Some(vertx.createHttpServer().requestHandler(router))
      this
    }
  }
}
