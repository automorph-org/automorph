package automorph.transport.http.server

import automorph.log.Logging
import automorph.spi.{EffectSystem, RequestHandler, ServerTransport}
import automorph.transport.http.endpoint.{JettyHttpEndpoint, JettyWebSocketEndpoint}
import automorph.transport.http.server.JettyServer.Context
import automorph.transport.http.{HttpContext, HttpMethod}
import jakarta.servlet.http.{HttpServletRequest, HttpServletResponse}
import jakarta.servlet.{DispatcherType, Filter, FilterChain, ServletContext, ServletRequest, ServletResponse}
import java.time.Duration
import java.util
import org.eclipse.jetty.server.Server
import org.eclipse.jetty.servlet.{FilterHolder, ServletContextHandler, ServletHolder}
import org.eclipse.jetty.util.component.AbstractLifeCycle
import org.eclipse.jetty.util.thread.{QueuedThreadPool, ThreadPool}
import org.eclipse.jetty.websocket.server.JettyWebSocketServerContainer
import org.eclipse.jetty.websocket.server.config.JettyWebSocketServletContainerInitializer
import scala.collection.immutable.ListMap
import scala.concurrent.duration.{FiniteDuration, DurationInt}
import scala.jdk.CollectionConverters.ListHasAsScala

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
  mapException: Throwable => Int = HttpContext.defaultExceptionToStatusCode,
  threadPool: ThreadPool = new QueuedThreadPool,
  idleTimeout: FiniteDuration = 30.seconds,
  maxFrameSize: Long = 65536,
  attributes: Map[String, String] = Map.empty,
  handler: RequestHandler[Effect, Context] = RequestHandler.dummy[Effect, Context],
) extends Logging with ServerTransport[Effect, Context] {

  private lazy val server = createServer()
  private val allowedMethods = methods.map(_.name).toSet
  private val methodFilter = new Filter {

    override def doFilter(request: ServletRequest, response: ServletResponse, chain: FilterChain): Unit = {
      val method = request.asInstanceOf[HttpServletRequest].getMethod
      Option.when(allowedMethods.contains(method.toUpperCase))(chain.doFilter(request, response)).getOrElse {
        response.asInstanceOf[HttpServletResponse].sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED)
      }
    }
  }

  override def withHandler(handler: RequestHandler[Effect, Context]): JettyServer[Effect] =
    copy(handler = handler)

  override def init(): Effect[Unit] =
    effectSystem.evaluate(this.synchronized {
      if (server.getState == AbstractLifeCycle.STARTED) {
        throw new IllegalStateException(s"${getClass.getSimpleName} already initialized")
      }
      server.start()
      server.getConnectors.foreach { connector =>
        connector.getProtocols.asScala.foreach { protocol =>
          logger.info("Listening for connections", ListMap("Protocol" -> protocol, "Port" -> port.toString))
        }
      }
    })

  override def close(): Effect[Unit] =
    effectSystem.evaluate(this.synchronized {
      if (server.getState == AbstractLifeCycle.STOPPED) {
        throw new IllegalStateException(s"${getClass.getSimpleName} already closed")
      }
      server.stop()
    })

  private def createServer(): Server = {
    val endpointTransport = JettyHttpEndpoint(effectSystem, mapException, handler)
    val servletHandler = new ServletContextHandler
    val servletPath = s"$pathPrefix*"

    // Validate URL path
    servletHandler.addServlet(new ServletHolder(endpointTransport.adapter), servletPath)

    // Validate HTTP request method
    servletHandler.addFilter(new FilterHolder(methodFilter), servletPath, util.EnumSet.of(DispatcherType.REQUEST))
    val server = new Server(port)
    attributes.foreach { case (name, value) =>
      server.setAttribute(name, value)
    }
    server.setHandler(servletHandler)

    // WebSocket support
    if (webSocket) {
      JettyWebSocketServletContainerInitializer
        .configure(
          servletHandler,
          (_: ServletContext, container: JettyWebSocketServerContainer) => {
            container.setIdleTimeout(Duration.ofNanos(idleTimeout.toNanos))
            container.setMaxFrameSize(maxFrameSize)
            container.addMapping(pathPrefix, JettyWebSocketEndpoint(effectSystem, mapException, handler).creator)
          },
        )
    }
    server
  }
}

case object JettyServer {

  /** Request context type. */
  type Context = JettyHttpEndpoint.Context
}
