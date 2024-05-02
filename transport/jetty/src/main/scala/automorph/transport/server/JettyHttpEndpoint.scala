package automorph.transport.server

import automorph.log.Logging
import automorph.spi.{EffectSystem, RequestHandler, ServerTransport}
import automorph.transport.HttpRequestHandler.{RequestData, ResponseData}
import automorph.transport.server.JettyHttpEndpoint.{Context, requestQuery}
import automorph.transport.{HttpContext, HttpMethod, HttpRequestHandler, Protocol}
import automorph.util.Extensions.{EffectOps, InputStreamOps}
import automorph.util.Network
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
 * @param handler
 *   RPC request handler
 * @tparam Effect
 *   effect type
 */
final case class JettyHttpEndpoint[Effect[_]](
  effectSystem: EffectSystem[Effect],
  mapException: Throwable => Int = HttpContext.toStatusCode,
  handler: RequestHandler[Effect, Context] = RequestHandler.dummy[Effect, Context],
) extends ServerTransport[Effect, Context, HttpServlet] with Logging {
  private lazy val httpServlet = new HttpServlet {
    implicit private val system: EffectSystem[Effect] = effectSystem

    override def service(request: HttpServletRequest, response: HttpServletResponse): Unit = {
      val asyncContext = request.startAsync()
      asyncContext.start { () =>
        httpRequestHandler.processRequest(request, (response, asyncContext)).runAsync
      }
    }
  }
  private val httpRequestHandler =
    HttpRequestHandler(receiveRequest, sendResponse, Protocol.Http, effectSystem, mapException, handler, logger)

  override def adapter: HttpServlet =
    httpServlet

  override def init(): Effect[Unit] =
    effectSystem.successful {}

  override def close(): Effect[Unit] =
    effectSystem.successful {}

  override def requestHandler(handler: RequestHandler[Effect, Context]): JettyHttpEndpoint[Effect] =
    copy(handler = handler)

  private def receiveRequest(request: HttpServletRequest): RequestData[Context] = {
    val query = requestQuery(request.getQueryString)
    RequestData(
      () => request.getInputStream.toByteArray,
      getRequestContext(request),
      Protocol.Http,
      s"${request.getRequestURI}$query",
      clientAddress(request),
      Some(request.getMethod),
    )
  }

  private def sendResponse(responseData: ResponseData[Context], channel: (HttpServletResponse, AsyncContext)): Unit = {
    val (response, asyncContext) = channel
    setResponseContext(response, responseData.context)
    response.setContentType(handler.mediaType)
    response.setStatus(responseData.statusCode)
    val outputStream = response.getOutputStream
    outputStream.write(responseData.body)
    outputStream.flush()
    outputStream.close()
    asyncContext.complete()
  }

  private def setResponseContext(response: HttpServletResponse, context: Option[Context]): Unit =
    context.toSeq.flatMap(_.headers).foreach { case (name, value) => response.setHeader(name, value) }

  private def getRequestContext(request: HttpServletRequest): Context = {
    val headers = request.getHeaderNames.asScala.flatMap { name =>
      request.getHeaders(name).asScala.map(value => name -> value)
    }.toSeq
    HttpContext(
      transportContext = Some(request),
      method = Some(HttpMethod.valueOf(request.getMethod)),
      headers = headers,
    ).url(request.getRequestURI)
  }

  private def clientAddress(request: HttpServletRequest): String = {
    val forwardedFor = Option(request.getHeader(HttpHeader.X_FORWARDED_FOR.name))
    val address = request.getRemoteAddr
    Network.address(forwardedFor, address)
  }
}

object JettyHttpEndpoint {

  /** Request context type. */
  type Context = HttpContext[HttpServletRequest]

  private[automorph] def requestQuery(query: String): String =
    Option(query).filter(_.nonEmpty).map("?" + _).getOrElse("")
}
