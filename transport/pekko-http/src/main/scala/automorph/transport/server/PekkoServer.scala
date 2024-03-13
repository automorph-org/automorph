package automorph.transport.server

import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.apache.pekko.actor.typed.{ActorSystem, Props}
import org.apache.pekko.http.scaladsl.Http
import org.apache.pekko.http.scaladsl.model.StatusCodes.{MethodNotAllowed, NotFound}
import org.apache.pekko.http.scaladsl.server.Directives.{complete, extractRequest}
import org.apache.pekko.http.scaladsl.server.Route
import org.apache.pekko.http.scaladsl.settings.ServerSettings
import automorph.log.Logging
import automorph.spi.{EffectSystem, RequestHandler, ServerTransport}
import automorph.transport.server.PekkoServer.Context
import automorph.transport.{HttpContext, HttpMethod, Protocol}
import com.typesafe.config.{Config, ConfigFactory}
import scala.collection.immutable.ListMap
import scala.concurrent.duration.{Duration, DurationInt, FiniteDuration}
import scala.concurrent.Await

/**
 * Pekko HTTP server transport plugin.
 *
 * Interprets HTTP request body as an RPC request and processes it using the specified RPC request handler.
 *   - The response returned by the RPC request handler is used as HTTP response body.
 *
 * @see
 *   [[https://en.wikipedia.org/wiki/HTTP Transport protocol]]
 * @see
 *   [[https://pekko.apache.org Library documentation]]
 * @see
 *   [[https://pekko.apache.org/api/pekko-http/current/ API]]
 * @constructor
 *   Creates and starts an Pekko HTTP server with specified RPC request handler.
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
final case class PekkoServer[Effect[_]](
  effectSystem: EffectSystem[Effect],
  port: Int,
  pathPrefix: String = "/",
  methods: Iterable[HttpMethod] = HttpMethod.values,
  mapException: Throwable => Int = HttpContext.toStatusCode,
  readTimeout: FiniteDuration = 30.seconds,
  serverSettings: ServerSettings = ServerSettings(""),
  config: Config = ConfigFactory.empty(),
  guardianProps: Props = Props.empty,
  handler: RequestHandler[Effect, Context] = RequestHandler.dummy[Effect, Context],
) extends ServerTransport[Effect, Context, Unit] with Logging {

  private lazy val route = createRoute()
  private val allowedMethods = methods.map(_.name).toSet
  private var server = Option.empty[(ActorSystem[Nothing], Http.ServerBinding)]

  override def endpoint: Unit =
    ()

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
          "Port" -> serverBinding.localAddress.getPort.toString,
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

  override def withHandler(handler: RequestHandler[Effect, Context]): PekkoServer[Effect] =
    copy(handler = handler)

  private def createRoute(): Route = {
    val endpointTransport = PekkoHttpEndpoint(effectSystem, mapException, readTimeout, handler)
    extractRequest { httpRequest =>
      // Validate HTTP request method
      if (allowedMethods.contains(httpRequest.method.value.toUpperCase)) {
        // Validate URL path
        if (httpRequest.uri.path.toString.startsWith(pathPrefix)) {
          endpointTransport.endpoint
        } else {
          complete(NotFound)
        }
      } else {
        complete(MethodNotAllowed)
      }
    }
  }
}

object PekkoServer {

  /** Request context type. */
  type Context = PekkoHttpEndpoint.Context
}
