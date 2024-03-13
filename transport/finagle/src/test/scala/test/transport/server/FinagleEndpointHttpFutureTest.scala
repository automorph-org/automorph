package test.transport.server

import automorph.spi.{EffectSystem, RequestHandler, ServerTransport}
import automorph.system.FutureSystem
import automorph.transport.server.FinagleHttpEndpoint
import com.twitter.finagle.{Http, ListeningServer}
import com.twitter.util.{Return, Throw}
import org.scalacheck.Arbitrary
import test.transport.server.FinagleEndpointHttpFutureTest.FinagleServer
import test.transport.{HttpContextGenerator, HttpServerTest}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Future, Promise}

final class FinagleEndpointHttpFutureTest extends HttpServerTest {

  type Effect[T] = Future[T]
  type Context = FinagleHttpEndpoint.Context

  override lazy val system: EffectSystem[Effect] = FutureSystem()

  override def run[T](effect: Effect[T]): T =
    await(effect)

  override def arbitraryContext: Arbitrary[Context] =
    HttpContextGenerator.arbitrary

  override def serverTransport(fixtureId: String): ServerTransport[Effect, Context, Unit] =
    FinagleServer(system, port(fixtureId))
}

object FinagleEndpointHttpFutureTest {

  type Effect[T] = Future[T]
  type Context = FinagleHttpEndpoint.Context

  final private case class FinagleServer(
    effectSystem: EffectSystem[Effect],
    port: Int,
  ) extends ServerTransport[Effect, Context, Unit] {
    private var rpcServer = FinagleHttpEndpoint(effectSystem)
    private var server = Option.empty[ListeningServer]

    override def endpoint: Unit =
      ()

    override def init(): Effect[Unit] =
      Future {
        server = Some(Http.serve(s":$port", rpcServer.endpoint))
      }

    override def close(): Effect[Unit] =
      server.map { activeServer =>
        val promise = Promise[Unit]()
        activeServer.close().respond {
          case Return(result) => promise.success(result)
          case Throw(error) => promise.failure(error)
        }
        promise.future
      }.getOrElse(effectSystem.successful {})

    override def withHandler(handler: RequestHandler[Effect, Context]): ServerTransport[Effect, Context, Unit] = {
      rpcServer = rpcServer.withHandler(handler)
      this
    }
  }
}
