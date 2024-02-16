package test.transport.websocket

import automorph.spi.{EffectSystem, EndpointTransport, RequestHandler, ServerTransport}
import automorph.system.ZioSystem
import automorph.transport.http.endpoint.ZioHttpWebSocketEndpoint
import org.scalacheck.{Arbitrary, Gen}
import test.transport.WebSocketServerTest
import test.transport.websocket.ZioEndpointHttpWebSocketZioTest.ZioServer
import zio.http.{Handler, Method, Routes, Server, handler}
import zio.{Task, Unsafe, ZIO, ZIOAppDefault}

class ZioHttpEndpointWebSocketZioTest extends WebSocketServerTest {

  type Effect[T] = Task[T]
  type Context = ZioHttpWebSocketEndpoint.Context

  override lazy val system: ZioSystem[Throwable] = ZioSystem.withTask

  override def run[T](effect: Effect[T]): T =
    Unsafe.unsafe { implicit unsafe =>
      system.runtime.unsafe.run(effect).toTry.get
    }

  override def arbitraryContext: Arbitrary[Context] =
    Arbitrary(Gen.const(()))

  override def serverTransport(fixtureId: String): ServerTransport[Effect, Context] =
    ZioServer(system, port(fixtureId))

  override def endpointTransport: EndpointTransport[Task, Context, ?] =
    ZioHttpWebSocketEndpoint(system)
}

object ZioEndpointHttpWebSocketZioTest {

  type Effect[T] = Task[T]
  type Context = ZioHttpWebSocketEndpoint.Context

  final case class ZioServer(
    effectSystem: EffectSystem[Effect],
    port: Int,
  ) extends ServerTransport[Effect, Context] with ZIOAppDefault {

//    private var server = Option.empty[ListeningServer]
    private lazy val webSocketApp = Handler.webSocket[Any](endpoint.adapter)
    private lazy val httpApp = Routes(Method.POST / "/" -> handler(webSocketApp.toResponse)).toHttpApp
    private var endpoint = ZioHttpWebSocketEndpoint(effectSystem)

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
