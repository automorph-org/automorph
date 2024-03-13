//package test.transport.endpoint
//
//import automorph.spi.{EffectSystem, EndpointTransport, RequestHandler, ServerTransport}
//import automorph.system.ZioSystem
//import automorph.transport.endpoint.ZioHttpEndpoint
//import scala.concurrent.ExecutionContext.Implicits.global
//import org.scalacheck.Arbitrary
//import test.transport.endpoint.ZioEndpointHttpZioTest.ZioServer
//import test.transport.{HttpContextGenerator, HttpServerTest}
//import zio.http.{Method, Routes, Server}
//import zio.{CancelableFuture, Console, Promise, Runtime, Task, Unsafe, ZIO, ZIOAppDefault}
//import scala.concurrent.Await
//import scala.concurrent.duration.Duration
//
//final class ZioHttpEndpointHttpZioTest extends HttpServerTest {
//
//  type Effect[T] = Task[T]
//  type Context = ZioHttpEndpoint.Context
//
////  private lazy val runtime = Unsafe.unsafe { implicit unsage =>
////    Runtime.unsafe.fromLayer(ZLayer.empty)
////  }
//
//  override lazy val system: ZioSystem[Throwable] = {
//    implicit val runtime: Runtime[Any] = Runtime.default
//    ZioSystem()
//  }
//
//  override def run[T](effect: Effect[T]): T = {
//    Unsafe.unsafe { implicit unsafe =>
//      system.runtime.unsafe.run(effect).toTry.get
////      runtime.unsafe.run(effect).toTry.get
//    }
//  }
//
//  override def arbitraryContext: Arbitrary[Context] =
//    HttpContextGenerator.arbitrary
//
//  override def serverTransport(fixtureId: String): ServerTransport[Effect, Context, Unit] =
//    ZioServer(system, port(fixtureId))
//
//  override def beforeAll(): Unit = {
//    super.beforeAll()
//  }
//
//  override def afterAll(): Unit = {
//    super.beforeAll()
//  }
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
////    var server = Option.empty[Promise[Throwable, Unit]]
//    private var server = Option.empty[CancelableFuture[Unit]]
//    private var endpoint = ZioHttpEndpoint(effectSystem)
//
//    override def run: ZIO[Any, Throwable, Unit] =
//      Promise.make[Throwable, Unit].flatMap { promise =>
////        server = Some(promise)
//        Server.install(httpApp).flatMap { port =>
//          promise.succeed(())
//        }
//      }.flatMap(_ => ZIO.never).onTermination(
//        _ => Console.printLine("STOP").asInstanceOf[zio.URIO[Server, Any]]
//      ).provide(Server.defaultWithPort(port)).unit
//
//    override def withHandler(handler: RequestHandler[Effect, Context]): ServerTransport[Effect, Context, Unit] = {
//      endpoint = endpoint.withHandler(handler)
//      this
//    }
//
//    override def init(): Effect[Unit] = {
//      Unsafe.unsafe { implicit unsafe =>
//        val future = Runtime.default.unsafe.runToFuture(run)
//        server = Some(future)
//        //      runtime.unsafe.run(effect).toTry.get
//        ZIO.succeed(())
//      }
//      println("DDD")
//      ZIO.succeed(())
//    }
////      run.flatMap(_ => server.map(_.await.map(_ => println("INIT"))).getOrElse(ZIO.succeed(())))
//
//    override def close(): Effect[Unit] = {
//      server.map(future => Await.result(future.cancel().map(_ => ()), Duration.Inf)).getOrElse(())
//      ZIO.succeed(())
//      //      server.map(_.await.flatMap(_ => exit(ExitCode(0)))).getOrElse(ZIO.succeed(()))
//    }
//  }
//}
