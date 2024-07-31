//package test.transport.server
//
//import automorph.spi.{EffectSystem, ServerTransport}
//import automorph.system.FutureSystem
//import automorph.transport.server.{JettyHttpEndpoint, JettyServer}
//import org.scalacheck.Arbitrary
//import test.transport.{HttpContextGenerator, HttpServerTest}
//import scala.concurrent.ExecutionContext.Implicits.global
//import scala.concurrent.Future
//
//final class JettyServerHttpFutureTest extends HttpServerTest {
//
//  type Effect[T] = Future[T]
//  type Context = JettyHttpEndpoint.Context
//
//  override lazy val system: EffectSystem[Effect] = FutureSystem()
//
//  override def run[T](effect: Effect[T]): T =
//    await(effect)
//
//  override def arbitraryContext: Arbitrary[Context] =
//    HttpContextGenerator.arbitrary
//
//  override def serverTransport(fixtureId: String): ServerTransport[Effect, Context, Unit] = {
//    System.setProperty("org.eclipse.jetty.LEVEL", "ERROR")
//    JettyServer(system, port(fixtureId))
//  }
//}
