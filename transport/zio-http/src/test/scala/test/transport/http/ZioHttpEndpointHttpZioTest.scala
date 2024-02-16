package test.transport.http

import automorph.spi.{EffectSystem, EndpointTransport, RequestHandler, ServerTransport}
import automorph.system.ZioSystem
import automorph.transport.http.endpoint.ZioHttpEndpoint
import org.scalacheck.Arbitrary
import test.transport.HttpServerTest
import test.transport.http.ZioEndpointHttpZioTest.ZioServer
import zio.http.{Method, Routes, Server}
import zio.{Task, Unsafe, ZIO, ZIOAppDefault}

class ZioHttpEndpointHttpZioTest extends HttpServerTest {

  type Effect[T] = Task[T]
  type Context = ZioHttpEndpoint.Context

  override lazy val system: ZioSystem[Throwable] = ZioSystem.apply

  override def run[T](effect: Effect[T]): T =
    Unsafe.unsafe { implicit unsafe =>
      system.runtime.unsafe.run(effect).toTry.get
    }

  override def arbitraryContext: Arbitrary[Context] =
    HttpContextGenerator.arbitrary

  override def serverTransport(fixtureId: String): ServerTransport[Effect, Context] =
    ZioServer(system, port(fixtureId))

  override def endpointTransport: EndpointTransport[Task, Context, ?] =
    ZioHttpEndpoint(system)
}

object ZioEndpointHttpZioTest {

  type Effect[T] = Task[T]
  type Context = ZioHttpEndpoint.Context

  final case class ZioServer(
    effectSystem: EffectSystem[Effect],
    port: Int,
  ) extends ServerTransport[Effect, Context] with ZIOAppDefault {

//    private var server = Option.empty[ListeningServer]
    private lazy val httpApp = Routes(Method.POST / "/" -> endpoint.adapter).toHttpApp
    private var endpoint = ZioHttpEndpoint(effectSystem)

    override def run: ZIO[Any, Throwable, Nothing] =
      Server.serve(httpApp).provide(Server.default)

    override def withHandler(handler: RequestHandler[Effect, Context]): ServerTransport[Effect, Context] = {
      endpoint = endpoint.withHandler(handler)
      this
    }

    override def init(): Effect[Unit] =
      run

    override def close(): Effect[Unit] =
      ???
  }
}
