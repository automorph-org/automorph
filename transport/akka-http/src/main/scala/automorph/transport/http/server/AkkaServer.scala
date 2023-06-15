package automorph.transport.http.server

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorSystem, Props}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.StatusCodes.{MethodNotAllowed, NotFound}
import akka.http.scaladsl.server.Directives.{complete, extractRequest}
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.settings.ServerSettings
import automorph.log.Logging
import automorph.spi.{EffectSystem, RequestHandler, ServerTransport}
import automorph.transport.http.endpoint.AkkaHttpEndpoint
import automorph.transport.http.server.AkkaServer.Context
import automorph.transport.http.{HttpContext, HttpMethod, Protocol}
import com.typesafe.config.{Config, ConfigFactory}
import scala.collection.immutable.ListMap
import scala.concurrent.duration.{Duration, FiniteDuration, DurationInt}
import scala.concurrent.Await

/**
 * Akka HTTP server transport plugin.
 *
 * Interprets HTTP request body as an RPC request and processes it using the specified RPC request handler.
 *   - The response returned by the RPC request handler is used as HTTP response body.
 *
 * @see
 *   [[https://en.wikipedia.org/wiki/HTTP Transport protocol]]
 * @see
 *   [[https://doc.akka.io/docs/akka-http Library documentation]]
 * @see
 *   [[https://doc.akka.io/api/akka-http/current/akka/http/ API]]
 * @constructor
 *   Creates and starts an Akka HTTP server with specified RPC request handler.
 * @param effectSystem
 *   effect system plugin
 * @param port
 *   port to listen on for HTTP connections
 * @param pathPrefix
 *   HTTP URL path prefix, only requests starting with this path prefix are allowed
 * @param methods
 *   allowed HTTP request methods
 * @param mapException
 *   maps an exception to a corresponding HTTP status code
 * @param readTimeout
 *   request read timeout
 * @param handler
 *   RPC request handler
 * @param serverSettings
 *   HTTP server settings
 * @param config
 *   actor system configuration
 * @param guardianProps
 *   guardian actor properties
 * @tparam Effect
 *   effect type
 */
final case class AkkaServer[Effect[_]](
  effectSystem: EffectSystem[Effect],
  port: Int,
  pathPrefix: String = "/",
  methods: Iterable[HttpMethod] = HttpMethod.values,
  mapException: Throwable => Int = HttpContext.defaultExceptionToStatusCode,
  readTimeout: FiniteDuration = 30.seconds,
  serverSettings: ServerSettings = ServerSettings(""),
  config: Config = ConfigFactory.empty(),
  guardianProps: Props = Props.empty,
  handler: RequestHandler[Effect, Context] = RequestHandler.dummy[Effect, Context],
) extends Logging with ServerTransport[Effect, Context] {

  private lazy val route = createRoute()
  private val allowedMethods = methods.map(_.name).toSet
  private var server = Option.empty[(ActorSystem[Nothing], Http.ServerBinding)]

  override def withHandler(handler: RequestHandler[Effect, Context]): AkkaServer[Effect] =
    copy(handler = handler)

  override def init(): Effect[Unit] =
    effectSystem.evaluate(this.synchronized {
      // Create HTTP endpoint route
      implicit val actorSystem: ActorSystem[Any] =
        ActorSystem(Behaviors.empty[Any], getClass.getSimpleName, config, guardianProps)

      // Start HTTP server
      val serverBinding = Await.result(
        Http().newServerAt("0.0.0.0", port).withSettings(serverSettings).bind(route),
        Duration.Inf,
      )
      logger.info(
        "Listening for connections",
        ListMap(
          "Protocol" -> Protocol.Http,
          "Port" -> serverBinding.localAddress.getPort.toString
        ),
      )
      server = Some((actorSystem, serverBinding))
    })

  override def close(): Effect[Unit] =
    effectSystem.evaluate(this.synchronized {
      server.fold(
        throw new IllegalStateException(s"${getClass.getSimpleName} already closed")
      ) { case (actorSystem, serverBinding) =>
        Await.result(serverBinding.unbind(), Duration.Inf)
        actorSystem.terminate()
        Await.result(actorSystem.whenTerminated, Duration.Inf)
        server = None
      }
    })

  private def createRoute(): Route = {
    val endpointTransport = AkkaHttpEndpoint(effectSystem, mapException, readTimeout, handler)
    extractRequest { httpRequest =>
      // Validate HTTP request method
      if (allowedMethods.contains(httpRequest.method.value.toUpperCase)) {
        // Validate URL path
        if (httpRequest.uri.path.toString.startsWith(pathPrefix)) {
          endpointTransport.adapter
        } else {
          complete(NotFound)
        }
      } else {
        complete(MethodNotAllowed)
      }
    }
  }
}

case object AkkaServer {

  /** Request context type. */
  type Context = AkkaHttpEndpoint.Context
}
