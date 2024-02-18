//package test.transport.endpoint
//
//import automorph.spi.{EffectSystem, EndpointTransport, RequestHandler, ServerTransport}
//import automorph.system.ZioSystem
//import automorph.transport.endpoint.ZioHttpEndpoint
//import org.scalacheck.Arbitrary
//import test.transport.{HttpContextGenerator, HttpServerTest}
//import test.transport.ZioEndpointHttpZioTest.ZioServer
//import zio.http.{Method, Routes, Server}
//import zio.{ExitCode, Promise, Runtime, Task, Unsafe, ZIO, ZIOAppDefault}
//
//final class ZioHttpEndpointHttpZioTest extends HttpServerTest {
//
//  type Effect[T] = Task[T]
//  type Context = ZioHttpEndpoint.Context
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
//    HttpContextGenerator.arbitrary
//
//  override def serverTransport(fixtureId: String): ServerTransport[Effect, Context] =
//    ZioServer(system, port(fixtureId))
//
//  override def endpointTransport: EndpointTransport[Task, Context, ?] =
//    ZioHttpEndpoint(system)
//}
//
//object ZioEndpointHttpZioTest {
//
//  type Effect[T] = Task[T]
//  type Context = ZioHttpEndpoint.Context
//
//  final case class ZioServer(
//    effectSystem: EffectSystem[Effect],
//    port: Int,
//  ) extends ServerTransport[Effect, Context] with ZIOAppDefault {
//
//    private lazy val httpApp = Routes(Method.POST / "/" -> endpoint.adapter).toHttpApp
//    var server = Option.empty[Promise[Throwable, Unit]]
//    private var endpoint = ZioHttpEndpoint(effectSystem)
//
//    override def run: ZIO[Any, Throwable, Unit] =
//      Promise.make[Throwable, Unit].flatMap { promise =>
//        server = Some(promise)
//        Server.install(httpApp).flatMap { _ =>
//          println("START")
//          promise.succeed(())
//        }
//      }.flatMap(_ => ZIO.never).forkDaemon.provide(Server.defaultWithPort(port)).ignore
//
//    override def withHandler(handler: RequestHandler[Effect, Context]): ServerTransport[Effect, Context] = {
//      endpoint = endpoint.withHandler(handler)
//      this
//    }
//
//    override def init(): Effect[Unit] =
//      run.flatMap(_ => server.map(_.await.map(_ => println("INIT"))).getOrElse(ZIO.succeed(())))
//
//    override def close(): Effect[Unit] =
//      server.map(_.await.flatMap(_ => exit(ExitCode(0)))).getOrElse(ZIO.succeed(()))
//  }
//}
