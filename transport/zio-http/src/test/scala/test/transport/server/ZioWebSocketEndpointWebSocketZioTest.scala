//package test.transport.endpoint
//
//import automorph.spi.{EffectSystem, EndpointTransport, RequestHandler, ServerTransport}
//import automorph.system.ZioSystem
//import automorph.transport.endpoint.ZioHttpWebSocketEndpoint
//import org.scalacheck.{Arbitrary, Gen}
//import test.transport.{HttpContextGenerator, WebSocketServerTest}
//import test.transport.websocket.ZioEndpointHttpWebSocketZioTest.ZioServer
//import zio.http.{Handler, Method, Routes, Server, handler}
//import zio.{Runtime, Task, Unsafe, ZIO, ZIOAppDefault}
//
//final class ZioWebSocketEndpointWebSocketZioTest extends WebSocketServerTest {
//
//  type Effect[T] = Task[T]
//  type Context = ZioHttpWebSocketEndpoint.Context
//
//  override lazy val system: ZioSystem[Throwable] = {
//    implicit val runtime: Runtime[Any] = Runtime.default
//    ZioSystem()
//  }
//
//  override def run[T](effect: Effect[T]): T =
//    Unsafe.unsafe { implicit unsafe =>
//      system.runtime.unsafe.run(effect).toTry.get
//    }
//
//  override def arbitraryContext: Arbitrary[Context] =
//    Arbitrary(Gen.const(()))
//
//  override def serverTransport(fixtureId: String): ServerTransport[Effect, Context, Unit] =
//    ZioServer(system, port(fixtureId))
//}
//
//object ZioEndpointHttpWebSocketZioTest {
//
//  type Effect[T] = Task[T]
//  type Context = ZioHttpWebSocketEndpoint.Context
//
//  final case class ZioServer(
//    effectSystem: EffectSystem[Effect],
//    port: Int,
//  ) extends ServerTransport[Effect, Context] with ZIOAppDefault {
//
//    private lazy val webSocketApp = Handler.webSocket[Any](endpoint.adapter)
//    private lazy val httpApp = Routes(Method.POST / "/" -> handler(webSocketApp.toResponse)).toHttpApp
//    private var endpoint = ZioHttpWebSocketEndpoint(effectSystem)
//
//    override def run: ZIO[Any, Throwable, Nothing] =
//      Server.serve(httpApp).provide(Server.defaultWithPort(port))
//
//    override def requestHandler(handler: RequestHandler[Effect, Context]): ServerTransport[Effect, Context, Unit] = {
//      endpoint = endpoint.requestHandler(handler)
//      this
//    }
//
//    override def init(): Effect[Unit] =
//      run
//
//    override def close(): Effect[Unit] =
//      exit(ExitCode(0))
//  }
//}