package automorph.transport.server

import automorph.log.{LogProperties, Logging, MessageLog}
import automorph.spi.{EffectSystem, RequestHandler, ServerTransport}
import automorph.transport.server.JettyHttpEndpoint.{Context, requestProperties}
import automorph.transport.{HttpContext, HttpMethod, Protocol}
import automorph.util.Extensions.{EffectOps, InputStreamOps, StringOps, ThrowableOps, TryOps}
import automorph.util.{Network, Random}
import jakarta.servlet.AsyncContext
import jakarta.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}
import org.eclipse.jetty.http.{HttpHeader, HttpStatus}
import scala.collection.immutable.ListMap
import scala.jdk.CollectionConverters.EnumerationHasAsScala
import scala.util.Try

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
    private val log = MessageLog(logger, Protocol.Http.name)

    override def service(request: HttpServletRequest, response: HttpServletResponse): Unit = {
      // Log the request
      val asyncContext = request.startAsync()
      asyncContext.start { () =>
        val requestId = Random.id
        lazy val requestProperties = getRequestProperties(request, requestId)
        log.receivedRequest(requestProperties)
        val requestBody = request.getInputStream.toByteArray

        // Process the request
        Try {
          val handlerResult = handler.processRequest(requestBody, getRequestContext(request), requestId)
          handlerResult.either.map(
            _.fold(
              error => sendErrorResponse(error, response, asyncContext, request, requestId, requestProperties),
              result => {
                // Send the response
                val responseBody = result.map(_.responseBody).getOrElse(Array.emptyByteArray)
                val status = result.flatMap(_.exception).map(mapException).getOrElse(HttpStatus.OK_200)
                sendResponse(
                  responseBody,
                  status,
                  result.flatMap(_.context),
                  response,
                  asyncContext,
                  request,
                  requestId,
                )
              },
            )
          ).runAsync
        }.failed.foreach { error =>
          sendErrorResponse(error, response, asyncContext, request, requestId, requestProperties)
        }
      }
    }
  }

  private val log = MessageLog(logger, Protocol.Http.name)
  implicit private val system: EffectSystem[Effect] = effectSystem

  override def endpoint: HttpServlet =
    httpServlet

  override def init(): Effect[Unit] =
    effectSystem.successful {}

  override def close(): Effect[Unit] =
    effectSystem.successful {}

  override def withHandler(handler: RequestHandler[Effect, Context]): JettyHttpEndpoint[Effect] =
    copy(handler = handler)

  private def sendErrorResponse(
    error: Throwable,
    response: HttpServletResponse,
    asyncContext: AsyncContext,
    request: HttpServletRequest,
    requestId: String,
    requestProperties: => Map[String, String],
  ): Unit = {
    log.failedProcessRequest(error, requestProperties)
    val responseBody = error.description.toByteArray
    val status = HttpStatus.INTERNAL_SERVER_ERROR_500
    sendResponse(responseBody, status, None, response, asyncContext, request, requestId)
  }

  private def sendResponse(
    responseBody: Array[Byte],
    status: Int,
    responseContext: Option[Context],
    response: HttpServletResponse,
    asyncContext: AsyncContext,
    request: HttpServletRequest,
    requestId: String,
  ): Unit = {
    // Log the response
    val responseStatus = responseContext.flatMap(_.statusCode).getOrElse(status)
    lazy val responseProperties = ListMap(
      LogProperties.requestId -> requestId,
      LogProperties.client -> clientAddress(request),
      "Status" -> responseStatus.toString,
    )
    log.sendingResponse(responseProperties)

    // Send the response
    Try {
      setResponseContext(response, responseContext)
      response.setContentType(handler.mediaType)
      response.setStatus(responseStatus)
      val outputStream = response.getOutputStream
      outputStream.write(responseBody)
      outputStream.flush()
      outputStream.close()
      asyncContext.complete()
      log.sentResponse(responseProperties)
    }.onError(error => log.failedSendResponse(error, responseProperties)).get
  }

  private def setResponseContext(response: HttpServletResponse, responseContext: Option[Context]): Unit =
    responseContext.toSeq.flatMap(_.headers).foreach { case (name, value) => response.setHeader(name, value) }

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

  private def getRequestProperties(request: HttpServletRequest, requestId: String): Map[String, String] =
    requestProperties(
      request.getRequestURI,
      request.getQueryString,
      request.getMethod,
      clientAddress(request),
      requestId,
    )

  private def clientAddress(request: HttpServletRequest): String = {
    val forwardedFor = Option(request.getHeader(HttpHeader.X_FORWARDED_FOR.name))
    val address = request.getRemoteAddr
    Network.address(forwardedFor, address)
  }
}

object JettyHttpEndpoint {

  /** Request context type. */
  type Context = HttpContext[HttpServletRequest]

  private[automorph] def requestProperties(
    uri: String,
    queryString: String,
    method: String,
    client: String,
    requestId: String,
  ): Map[String, String] = {
    val query = Option(queryString).filter(_.nonEmpty).map("?" + _).getOrElse("")
    val url = s"$uri$query"
    ListMap(
      LogProperties.requestId -> requestId,
      LogProperties.client -> client,
      "URL" -> url,
      "Method" -> method,
    )
  }
}
