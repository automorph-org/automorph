//package test.transport.endpoint
//
//import automorph.spi.{EffectSystem, EndpointTransport, RequestHandler, ServerTransport}
//import automorph.system.FutureSystem
//import automorph.transport.endpoint.PlayHttpEndpoint
//import org.scalacheck.Arbitrary
//import scala.concurrent.{Future, Promise}
//import scala.concurrent.ExecutionContext.Implicits.global
//import test.transport.{HttpContextGenerator, HttpServerTest}
//import test.transport.PlayEndpointHttpFutureTest.PlayServer
//
//final class PlayEndpointHttpFutureTest extends HttpServerTest {
//
//  type Effect[T] = Future[T]
//  type Context = PlayHttpEndpoint.Context
//
//  override lazy val system: EffectSystem[Effect] = FutureSystem()
//
//  override def run[T](effect: Effect[T]): T =
//    await(effect)
//
//  override def arbitraryContext: Arbitrary[Context] =
//    HttpContextGenerator.arbitrary
//
//  override def serverTransport(fixtureId: String): ServerTransport[Effect, Context, Unit] =
//    PlayServer(system, port(fixtureId))
//}
//
//object PlayEndpointHttpFutureTest {
//
//  type Effect[T] = Future[T]
//  type Context = PlayHttpEndpoint.Context
//
//  private final case class PlayServer(
//    effectSystem: EffectSystem[Effect],
//    port: Int
//  ) extends ServerTransport[Effect, Context] {
//    private var endpoint = PlayHttpEndpoint(effectSystem)
//    private var server = Option.empty[ListeningServer]
//
//    override def requestHandler(handler: RequestHandler[Effect, Context]): ServerTransport[Effect, Context, Unit] = {
//      endpoint = endpoint.requestHandler(handler)
//      this
//    }
//
//    override def init(): Effect[Unit] =
//      Future {
//        server = Some(Http.serve(s":$port", endpoint))
//      }
//
//    override def close(): Effect[Unit] = {
//      server.map { activeServer =>
//        val promise = Promise[Unit]()
//        activeServer.close().respond {
//          case Return(result) => promise.success(result)
//          case Throw(error) => promise.failure(error)
//        }
//        promise.future
//      }.getOrElse(effectSystem.successful {})
//    }
//  }
//}
