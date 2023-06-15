package automorph.transport.http.server

import automorph.log.Logging
import automorph.spi.{EffectSystem, RequestHandler, ServerTransport}
import automorph.transport.http.endpoint.VertxHttpEndpoint
import automorph.transport.http.server.VertxServer.{Context, defaultVertxOptions}
import automorph.transport.http.{HttpContext, HttpMethod, Protocol}
import automorph.transport.websocket.endpoint.VertxWebSocketEndpoint
import io.vertx.core.http.{HttpServer, HttpServerOptions}
import io.vertx.core.{Vertx, VertxOptions}
import scala.collection.immutable.ListMap

/**
 * Vert.x HTTP & WebSocket server message transport plugin.
 *
 * Interprets HTTP request body as an RPC request and processes it using the specified RPC request handler.
 *   - The response returned by the RPC request handler is used as HTTP response body.
 *   - Processes only HTTP requests starting with specified URL path.
 *
 * @see
 *   [[https://en.wikipedia.org/wiki/Hypertext Transport protocol]]
 * @see
 *   [[https://en.wikipedia.org/wiki/WebSocket Alternative transport protocol]]
 * @see
 *   [[https://vertx.io Library documentation]]
 * @see
 *   [[https://vertx.io/docs/apidocs/index.html API]]
 * @constructor
 *   Creates an Vert.x HTTP & WebSocket server with specified RPC request handler.
 * @param effectSystem
 *   effect system plugin
 * @param port
 *   port to listen on for HTTP connections
 * @param pathPrefix
 *   HTTP URL path prefix, only requests starting with this path prefix are allowed
 * @param methods
 *   allowed HTTP request methods
 * @param webSocket
 *   support upgrading of HTTP connections to use WebSocket protocol if true, support HTTP only if false
 * @param mapException
 *   maps an exception to a corresponding HTTP status code
 * @param vertxOptions
 *   VertX options
 * @param httpServerOptions
 *   HTTP server options
 * @param handler
 *   RPC request handler
 * @tparam Effect
 *   effect type
 */
final case class VertxServer[Effect[_]](
  effectSystem: EffectSystem[Effect],
  port: Int,
  pathPrefix: String = "/",
  methods: Iterable[HttpMethod] = HttpMethod.values,
  webSocket: Boolean = true,
  mapException: Throwable => Int = HttpContext.defaultExceptionToStatusCode,
  vertxOptions: VertxOptions = defaultVertxOptions,
  httpServerOptions: HttpServerOptions = new HttpServerOptions,
  handler: RequestHandler[Effect, Context] = RequestHandler.dummy[Effect, Context],
) extends Logging with ServerTransport[Effect, Context] {

  private lazy val httpServer = createServer()
  private val statusWebSocketApplication = 4000
  private val statusNotFound = 404
  private val statusMethodNotAllowed = 405
  private val messageNotFound = "Not Found"
  private val messageMethodNotAllowed = "Method Not Allowed"
  private val allowedMethods = methods.map(_.name).toSet

  override def withHandler(handler: RequestHandler[Effect, Context]): VertxServer[Effect] =
    copy(handler = handler)

  override def init(): Effect[Unit] = {
    effectSystem.evaluate(this.synchronized {
      val server = httpServer.listen().toCompletionStage.toCompletableFuture.get()
      (Seq(Protocol.Http) ++ Option.when(webSocket)(Protocol.WebSocket)).foreach { protocol =>
        logger.info("Listening for connections", ListMap(
          "Protocol" -> protocol,
          "Port" -> server.actualPort.toString
        ))
      }
    })
  }

  override def close(): Effect[Unit] = {
    effectSystem.evaluate(this.synchronized {
      httpServer.close().toCompletionStage.toCompletableFuture.get()
      ()
    })
  }

  private def createServer(): HttpServer = {
    // HTTP
    val endpoint = VertxHttpEndpoint(effectSystem, mapException, handler)
    val server = Vertx.vertx(vertxOptions).createHttpServer(httpServerOptions.setPort(port)).requestHandler { request =>
      // Validate URL path
      if (request.path.startsWith(pathPrefix)) {
        // Validate HTTP request method
        if (allowedMethods.contains(request.method.name.toUpperCase)) {
          endpoint.adapter.handle(request)
        } else {
          request.response.setStatusCode(statusMethodNotAllowed).end(messageMethodNotAllowed)
          ()
        }
      } else {
        request.response.setStatusCode(statusNotFound).end(messageNotFound)
        ()
      }
    }

    // WebSocket
    Option.when(webSocket) {
      val webSocketHandler = VertxWebSocketEndpoint(effectSystem, handler)
      server.webSocketHandler { request =>
        // Validate URL path
        if (request.path.startsWith(pathPrefix)) {
          webSocketHandler.handle(request)
        } else {
          request.close((statusWebSocketApplication + statusNotFound).toShort, messageNotFound)
          ()
        }
      }
    }.getOrElse(server)
  }
}

case object VertxServer {

  /** Request context type. */
  type Context = VertxHttpEndpoint.Context

  /**
   * Default Vert.x server options providing the following settings.
   * - Event loop threads: 2 * number of CPU cores
   * - Worker threads: number of CPU cores
   */
  def defaultVertxOptions: VertxOptions =
    new VertxOptions().setEventLoopPoolSize(Runtime.getRuntime.availableProcessors * 2)
      .setWorkerPoolSize(Runtime.getRuntime.availableProcessors)
}
