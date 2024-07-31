package automorph.transport.server

import automorph.log.Logging
import automorph.spi.{EffectSystem, RpcHandler, ServerTransport}
import automorph.transport.server.JettyServer.Context
import automorph.transport.{HttpContext, HttpMethod}
import org.eclipse.jetty.http.HttpStatus
import org.eclipse.jetty.server.{Handler, Request, Response, Server, ServerConnector}
import org.eclipse.jetty.util.Callback
import org.eclipse.jetty.util.thread.{QueuedThreadPool, ThreadPool}
import scala.collection.immutable.ListMap
import scala.concurrent.duration.{DurationInt, FiniteDuration}
import scala.jdk.CollectionConverters.{CollectionHasAsScala, ListHasAsScala}

/**
 * Jetty HTTP & WebSocket server message transport plugin.
 *
 * Interprets HTTP request body as an RPC request and processes it using the specified RPC request handler.
 *   - The response returned by the RPC request handler is used as HTTP response body.
 *   - Processes only HTTP requests starting with specified URL path.
 *
 * @see
 *   [[https://en.wikipedia.org/wiki/HTTP Transport protocol]]
 * @see
 *   [[https://jetty.io Library documentation]]
 * @see
 *   [[https://www.javadoc.io/doc/io.jetty/jetty-core/latest/index.html API]]
 * @constructor
 *   Creates a Jetty HTTP server with specified RPC request handler.
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
 * @param threadPool
 *   thread pool
 * @param handler
 *   RPC request handler
 * @param idleTimeout
 *   idle WebSocket connection timeout
 * @param maxFrameSize
 *   maximum WebSocket frame size
 * @param attributes
 *   server attributes
 * @tparam Effect
 *   effect type
 */
final case class JettyServer[Effect[_]](
  effectSystem: EffectSystem[Effect],
  port: Int,
  pathPrefix: String = "/",
  methods: Iterable[HttpMethod] = HttpMethod.values,
  webSocket: Boolean = true,
  mapException: Throwable => Int = HttpContext.toStatusCode,
  threadPool: ThreadPool = new QueuedThreadPool,
  idleTimeout: FiniteDuration = 30.seconds,
  maxFrameSize: Long = 65536,
  attributes: Map[String, String] = Map.empty,
  handler: RpcHandler[Effect, Context] = RpcHandler.dummy[Effect, Context],
) extends ServerTransport[Effect, Context, Unit] with Logging {

  private lazy val server = createServer()
  private val allowedMethods = methods.map(_.name).toSet
  private val httpHandler = new Handler.Abstract() {
    private val endpointTransport = JettyHttpEndpoint(effectSystem, mapException, handler)

    override def handle(request: Request, response: Response, callback: Callback): Boolean =
      // Validate HTTP request method
      if (!allowedMethods.contains(request.getMethod.toUpperCase)) {
        Response.writeError(request, response, callback, HttpStatus.METHOD_NOT_ALLOWED_405)
        true
        // Validate URL path
      } else if (!request.getHttpURI.getPath.startsWith(pathPrefix)) {
        Response.writeError(request, response, callback, HttpStatus.NOT_FOUND_404)
        true
      } else {
        endpointTransport.adapter.handle(request, response, callback)
      }
  }

  override def adapter: Unit =
    ()

  override def init(): Effect[Unit] =
    effectSystem.evaluate(this.synchronized {
      if (server.isStarted || server.isStarting) {
        throw new IllegalStateException(s"${getClass.getSimpleName} already initialized")
      }
      server.start()
      server.getConnectors.foreach { connector =>
        connector.getConnectedEndPoints.asScala.map(_.getLocalSocketAddress)
        connector.getProtocols.asScala.foreach { protocol =>
          logger.info("Listening for connections", ListMap("Protocol" -> protocol, "Port" -> port.toString))
        }
      }
    })

  override def close(): Effect[Unit] =
    effectSystem.evaluate(this.synchronized {
      if (server.isStopped || server.isStopping) {
        throw new IllegalStateException(s"${getClass.getSimpleName} already closed")
      }
      server.stop()
    })

  override def rpcHandler(handler: RpcHandler[Effect, Context]): JettyServer[Effect] =
    copy(handler = handler)

  private def createServer(): Server = {
    val server = new Server(threadPool)
    val connector = new ServerConnector(server)
    connector.setPort(port)
    server.addConnector(connector)
    server.setHandler(httpHandler)

    // WebSocket support
//    if (webSocket) {
//      JettyWebSocketServletContainerInitializer
//        .configure(
//          servletHandler,
//          (_: ServletContext, container: JettyWebSocketServerContainer) => {
//            container.setIdleTimeout(Duration.ofNanos(idleTimeout.toNanos))
//            container.setMaxFrameSize(maxFrameSize)
//            container.addMapping(pathPrefix, JettyWebSocketEndpoint(effectSystem, mapException, handler).creator)
//          },
//        )
//    }
    server
  }
}

object JettyServer {

  /** Request context type. */
  type Context = JettyHttpEndpoint.Context
}
