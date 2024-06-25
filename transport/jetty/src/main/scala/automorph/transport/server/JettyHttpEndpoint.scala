package automorph.transport.server

import automorph.spi.{EffectSystem, RpcHandler, ServerTransport}
import automorph.transport.HttpContext.headerRpcNodeId
import automorph.transport.server.JettyHttpEndpoint.{Context, requestQuery}
import automorph.transport.{ClientServerHttpHandler, HttpContext, HttpMetadata, HttpMethod, Protocol, ServerHttpHandler}
import automorph.util.Extensions.{EffectOps, InputStreamOps}
import jakarta.servlet.AsyncContext
import jakarta.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}
import org.eclipse.jetty.http.HttpHeader
import scala.jdk.CollectionConverters.EnumerationHasAsScala

/**
 * Jetty HTTP endpoint message transport plugin.
 *
 * Interprets HTTP request body as an RPC request and processes it with the specified RPC handler.
 *   - The response returned by the RPC handler is used as HTTP response body.
 *
 * @see
 *   [[https://en.wikipedia.org/wiki/HTTP Transport protocol]]
 * @see
 *   [[https://www.eclipse.org/jetty Library documentation]]
 * @see
 *   [[https://www.eclipse.org/jetty/javadoc/jetty-11/index.html API]]
 * @constructor
 *   Creates an Jetty HTTP endpoint message transport plugin with specified effect system and request handler.
 * @param effectSystem
 *   effect system plugin
 * @param mapException
 *   maps an exception to a corresponding HTTP status code
 * @param rpcHandler
 *   RPC request handler
 * @tparam Effect
 *   effect type
 */
final case class JettyHttpEndpoint[Effect[_]](
  effectSystem: EffectSystem[Effect],
  mapException: Throwable => Int = HttpContext.toStatusCode,
  rpcHandler: RpcHandler[Effect, Context] = RpcHandler.dummy[Effect, Context],
) extends ServerTransport[Effect, Context, HttpServlet] {
  private lazy val httpServlet = new HttpServlet {
    implicit private val system: EffectSystem[Effect] = effectSystem

    override def service(request: HttpServletRequest, response: HttpServletResponse): Unit = {
      val asyncContext = request.startAsync()
      asyncContext.start { () =>
        handler.processRequest(request, (response, asyncContext)).runAsync
      }
    }
  }
  private val handler =
    ClientServerHttpHandler(receiveRequest, sendResponse, Protocol.Http, effectSystem, mapException, rpcHandler)

  override def adapter: HttpServlet =
    httpServlet

  override def init(): Effect[Unit] =
    effectSystem.successful {}

  override def close(): Effect[Unit] =
    effectSystem.successful {}

  override def rpcHandler(handler: RpcHandler[Effect, Context]): JettyHttpEndpoint[Effect] =
    copy(rpcHandler = handler)

  private def receiveRequest(request: HttpServletRequest): (Effect[Array[Byte]], Context) =
    effectSystem.evaluate(request.getInputStream.toByteArray) -> getRequestContext(request)

  private def sendResponse(
    body: Array[Byte],
    metadata: HttpMetadata[Context],
    channel: (HttpServletResponse, AsyncContext),
  ): Effect[Unit] = {
    val (response, asyncContext) = channel
    setResponseContext(response, metadata.context)
    response.setContentType(metadata.contentType)
    response.setStatus(metadata.statusCodeOrOk)
    effectSystem.evaluate {
      val outputStream = response.getOutputStream
      outputStream.write(body)
      outputStream.flush()
      outputStream.close()
      asyncContext.complete()
    }
  }

  private def setResponseContext(response: HttpServletResponse, context: Context): Unit =
    context.headers.foreach { case (name, value) => response.setHeader(name, value) }

  private def getRequestContext(request: HttpServletRequest): Context = {
    val headers = request.getHeaderNames.asScala.flatMap { name =>
      request.getHeaders(name).asScala.map(value => name -> value)
    }.toSeq
    val query = requestQuery(request.getQueryString)
    HttpContext(
      transportContext = Some(request),
      method = Some(HttpMethod.valueOf(request.getMethod)),
      headers = headers,
      peer = Some(client(request)),
    ).url(s"${request.getRequestURI}$query")
  }

  private def client(request: HttpServletRequest): String = {
    val address = request.getRemoteAddr
    val forwardedFor = Option(request.getHeader(HttpHeader.X_FORWARDED_FOR.name))
    val nodeId = Option(request.getHeader(headerRpcNodeId))
    ServerHttpHandler.client(address, forwardedFor, nodeId)
  }
}

object JettyHttpEndpoint {

  /** Request context type. */
  type Context = HttpContext[HttpServletRequest]

  private[automorph] def requestQuery(query: String): String =
    Option(query).filter(_.nonEmpty).map("?" + _).getOrElse("")
}
