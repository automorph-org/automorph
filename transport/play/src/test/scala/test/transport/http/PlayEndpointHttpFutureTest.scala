//package test.transport.http
//
//import automorph.spi.{EffectSystem, EndpointTransport, RequestHandler, ServerTransport}
//import automorph.system.FutureSystem
//import automorph.transport.http.endpoint.PlayHttpEndpoint
//import org.scalacheck.Arbitrary
//import scala.concurrent.{Future, Promise}
//import test.transport.HttpServerTest
//import test.transport.http.PlayEndpointHttpFutureTest.PlayServer
//
//class PlayEndpointHttpFutureTest extends HttpServerTest {
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
//  override def serverTransport(fixtureId: String): ServerTransport[Effect, Context] =
//    PlayServer(system, port(fixtureId))
//
//  override def endpointTransport: EndpointTransport[Future, Context, ?] =
//    PlayHttpEndpoint(system)
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
//    override def withHandler(handler: RequestHandler[Effect, Context]): ServerTransport[Effect, Context] = {
//      endpoint = endpoint.withHandler(handler)
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