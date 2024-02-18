package test.transport.endpoint

import automorph.spi.{EffectSystem, RequestHandler, ServerTransport}
import automorph.system.FutureSystem
import automorph.transport.endpoint.TapirHttpEndpoint
import com.linecorp.armeria.server.Server
import org.scalacheck.Arbitrary
import sttp.tapir.server.armeria.ArmeriaFutureServerInterpreter
import test.transport.endpoint.TapirArmeriaHttpFutureTest.TapirServer
import test.transport.{HttpContextGenerator, HttpServerTest}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

final class TapirArmeriaHttpFutureTest extends HttpServerTest {

  type Effect[T] = Future[T]
  type Context = TapirHttpEndpoint.Context

  override lazy val system: EffectSystem[Effect] = FutureSystem()
  override lazy val arbitraryContext: Arbitrary[Context] =
    HttpContextGenerator.arbitrary

  override def run[T](effect: Effect[T]): T =
    await(effect)

  def serverTransport(fixtureId: String): ServerTransport[Effect, Context] =
    TapirServer(system, port(fixtureId))
}

object TapirArmeriaHttpFutureTest {

  type Effect[T] = Future[T]
  type Context = TapirHttpEndpoint.Context

  final case class TapirServer(effectSystem: EffectSystem[Effect], port: Int) extends ServerTransport[Effect, Context] {
    private var endpoint = TapirHttpEndpoint(effectSystem)
    private var server = Option.empty[Server]

    override def withHandler(handler: RequestHandler[Effect, Context]): ServerTransport[Effect, Context] = {
      endpoint = endpoint.withHandler(handler)
      this
    }

    override def init(): Effect[Unit] =
      effectSystem.evaluate {
        val service = ArmeriaFutureServerInterpreter().toService(endpoint.adapter)
        val activeServer = Server.builder().http(port).service(service).build()
        activeServer.start().get()
        server = Some(activeServer)
      }

    override def close(): Effect[Unit] =
      effectSystem.evaluate {
        server.foreach { activeServer =>
          activeServer.stop().toCompletableFuture.get()
          activeServer.close()
        }
      }
  }
}
