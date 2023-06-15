package automorph.transport.http.server

import automorph.log.Logging
import automorph.spi.{EffectSystem, RequestHandler, ServerTransport}
import automorph.transport.http.endpoint.UndertowHttpEndpoint
import automorph.transport.http.server.UndertowServer.{Context, defaultBuilder}
import automorph.transport.http.{HttpContext, HttpMethod}
import automorph.transport.websocket.endpoint.UndertowWebSocketEndpoint
import io.undertow.predicate.Predicates
import io.undertow.server.handlers.ResponseCodeHandler
import io.undertow.server.{HttpHandler, HttpServerExchange}
import io.undertow.{Handlers, Undertow}
import java.net.InetSocketAddress
import scala.collection.immutable.ListMap
import scala.jdk.CollectionConverters.ListHasAsScala

/**
 * Undertow HTTP & WebSocket server message transport plugin.
 *
 * Interprets HTTP request body as an RPC request and processes it using the specified RPC request handler.
 *   - The response returned by the RPC request handler is used as HTTP response body.
 *   - Processes only HTTP requests starting with specified URL path.
 *
 * @see
 *   [[https://en.wikipedia.org/wiki/HTTP Transport protocol]]
 * @see
 *   [[https://en.wikipedia.org/wiki/WebSocket Alternative transport protocol]]
 * @see
 *   [[https://undertow.io Library documentation]]
 * @see
 *   [[https://www.javadoc.io/doc/io.undertow/undertow-core/latest/index.html API]]
 * @constructor
 *   Creates an Undertow HTTP & WebSocket server with specified effect system RPC request handler.
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
 * @param builder
 *   Undertow builder
 * @param handler
 *   RPC request andler
 * @tparam Effect
 *   effect type
 */
final case class UndertowServer[Effect[_]](
  effectSystem: EffectSystem[Effect],
  port: Int,
  pathPrefix: String = "/",
  methods: Iterable[HttpMethod] = HttpMethod.values,
  webSocket: Boolean = true,
  mapException: Throwable => Int = HttpContext.defaultExceptionToStatusCode,
  builder: Undertow.Builder = defaultBuilder,
  handler: RequestHandler[Effect, Context] = RequestHandler.dummy[Effect, Context],
) extends Logging with ServerTransport[Effect, Context] {

  private var active = false
  private lazy val server = createServer()
  private val allowedMethods = methods.map(_.name).toSet

  override def withHandler(handler: RequestHandler[Effect, Context]): UndertowServer[Effect] =
    copy(handler = handler)

  override def init(): Effect[Unit] =
    effectSystem.evaluate(this.synchronized {
      server.start()
      active = true
      server.getListenerInfo.asScala.foreach { listener =>
        logger.info(
          "Listening for connections",
          ListMap("Protocol" -> listener.getProtcol) ++
            (listener.getAddress match {
              case address: InetSocketAddress =>
                ListMap("Host" -> address.getHostString, "Port" -> address.getPort.toString)
              case _ => ListMap()
            }),
        )
      }
    })

  override def close(): Effect[Unit] =
    effectSystem.evaluate(this.synchronized {
      if (active) {
        server.stop()
        active = false
      } else {
        throw new IllegalStateException(s"${getClass.getSimpleName} already closed")
      }
    })

  private def createServer(): Undertow = {
    // Validate HTTP request method
    val endpointTransport = UndertowHttpEndpoint(effectSystem, mapException, handler)
    val httpHandler = methodHandler(endpointTransport.adapter)

    // Validate URL path
    val rootHandler = Handlers.predicate(
      Predicates.prefix(pathPrefix),
      // WebSocket support
      Option.when(webSocket)(
        UndertowWebSocketEndpoint(effectSystem, handler).handshakeHandler(httpHandler)
      ).getOrElse(httpHandler),
      ResponseCodeHandler.HANDLE_404,
    )
    builder.addHttpListener(port, "0.0.0.0", rootHandler).build()
  }

  private def methodHandler(handler: HttpHandler): HttpHandler =
    Handlers.predicate(
      (exchange: HttpServerExchange) => allowedMethods.contains(exchange.getRequestMethod.toString.toUpperCase),
      handler,
      ResponseCodeHandler.HANDLE_405,
    )
}

case object UndertowServer {

  /** Request context type. */
  type Context = UndertowHttpEndpoint.Context

  /**
   * Default Undertow server builder providing the following settings:
   *   - IO threads: 2 * number of CPU cores
   *   - Worker threads: number of CPU cores
   */
  def defaultBuilder: Undertow.Builder =
    Undertow.builder().setIoThreads(Runtime.getRuntime.availableProcessors * 2)
      .setWorkerThreads(Runtime.getRuntime.availableProcessors)
}
