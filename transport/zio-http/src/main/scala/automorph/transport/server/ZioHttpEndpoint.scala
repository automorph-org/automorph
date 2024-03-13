package automorph.transport.server

import automorph.log.{LogProperties, Logging, MessageLog}
import automorph.spi.{EffectSystem, ServerTransport, RequestHandler}
import automorph.transport.server.ZioHttpEndpoint.{Context, headerXForwardedFor}
import automorph.transport.{HttpContext, HttpMethod, Protocol}
import automorph.util.Extensions.{StringOps, ThrowableOps, TryOps}
import automorph.util.{Network, Random}
import zio.{Chunk, IO, Trace, ZIO, http}
import zio.http.{Body, Handler, Header, Headers, MediaType, Request, Response, Status}
import scala.collection.immutable.ListMap
import scala.util.Try

/**
 * ZIO HTTP endpoint message transport plugin.
 *
 * Interprets HTTP request body as a RPC request and processes it with the specified RPC handler.
 *   - The response returned by the RPC handler is used as HTTP response body.
 *
 * @see
 *   [[https://en.wikipedia.org/wiki/HTTP Transport protocol]]
 * @see
 *   [[https://zio.dev/zio-http Library documentation]]
 * @see
 *   [[https://javadoc.io/doc/dev.zio/zio-http_3/latest/index.html API]]
 * @constructor
 *   Creates an ZIO HTTP endpoint message transport plugin with specified effect system and request handler.
 * @param effectSystem
 *   effect system plugin
 * @param mapException
 *   maps an exception to a corresponding HTTP status code
 * @param handler
 *   RPC request handler
 * @tparam Fault
 *   ZIO error type
 */
final case class ZioHttpEndpoint[Fault](
  effectSystem: EffectSystem[({ type Effect[A] = IO[Fault, A] })#Effect],
  mapException: Throwable => Int = HttpContext.toStatusCode,
  handler: RequestHandler[({ type Effect[A] = IO[Fault, A] })#Effect, Context] =
    RequestHandler.dummy[({ type Effect[A] = IO[Fault, A] })#Effect, Context],
) extends ServerTransport[
    ({ type Effect[A] = IO[Fault, A] })#Effect,
    Context,
    http.RequestHandler[Any, Response],
  ]
  with Logging {

  private lazy val mediaType = MediaType.forContentType(handler.mediaType).getOrElse(
    throw new IllegalStateException(s"Invalid message content type: ${handler.mediaType}")
  )
  private lazy val requestHandler = Handler.fromFunctionZIO(handle)
  private val log = MessageLog(logger, Protocol.Http.name)

  override def endpoint: http.RequestHandler[Any, Response] =
    requestHandler

  override def init(): IO[Fault, Unit] =
    effectSystem.successful {}

  override def close(): IO[Fault, Unit] =
    effectSystem.successful {}

  override def withHandler(
    handler: RequestHandler[({ type Effect[A] = IO[Fault, A] })#Effect, Context]
  ): ZioHttpEndpoint[Fault] =
    copy(handler = handler)

  private def handle(request: Request): IO[Response, Response] = {
    // Log the request
    val requestId = Random.id
    lazy val requestProperties = getRequestProperties(request, requestId)
    log.receivedRequest(requestProperties)

    // Process the request
    Try {
      request.body.asArray.mapError { _ =>
        val message = s"Failed to read ${Protocol.Http.name} request body"
        createErrorResponse(message, implicitly[Trace], request, requestId, requestProperties)
      }.flatMap { requestBody =>
        val handlerResult = handler.processRequest(requestBody, getRequestContext(request), requestId)
        handlerResult.mapBoth(
          _ => {
            val message = s"Failed to process ${Protocol.Http.name} request"
            createErrorResponse(message, implicitly[Trace], request, requestId, requestProperties)
          },
          result => {
            // Create the response
            val responseBody = result.map(_.responseBody).getOrElse(Array.emptyByteArray)
            val status = result.flatMap(_.exception).map(mapException).flatMap(Status.fromInt).getOrElse(Status.Ok)
            createResponse(responseBody, status, result.flatMap(_.context), request, requestId)
          },
        )
      }
    }.foldError { error =>
      ZIO.fail(createErrorResponse(error, request, requestId, requestProperties))
    }
  }

  private def createErrorResponse(
    error: Throwable,
    request: Request,
    requestId: String,
    requestProperties: => Map[String, String],
  ): Response = {
    log.failedProcessRequest(error, requestProperties)
    val responseBody = error.trace.mkString("\n").toByteArray
    createResponse(responseBody, Status.InternalServerError, None, request, requestId)
  }

  private def createErrorResponse(
    message: String,
    trace: Trace,
    request: Request,
    requestId: String,
    requestProperties: => Map[String, String],
  ): Response = {
    logger.error(s"$message\n$trace", requestProperties)
    val responseBody = trace.toString.toByteArray
    createResponse(responseBody, Status.InternalServerError, None, request, requestId)
  }

  private def createResponse(
    responseBody: Array[Byte],
    status: Status,
    responseContext: Option[Context],
    request: Request,
    requestId: String,
  ): Response = {
    // Log the response
    val responseStatus = responseContext.flatMap(_.statusCode.flatMap(Status.fromInt)).getOrElse(status)
    lazy val responseProperties = ListMap(
      LogProperties.requestId -> requestId,
      LogProperties.client -> clientAddress(request),
      "Status" -> responseStatus.toString,
    )

    // Create the response
    val response = setResponseContext(
      Response(responseStatus, Headers(Header.ContentType(mediaType)), Body.fromChunk(Chunk.fromArray(responseBody))),
      responseContext,
    )
    log.sendingResponse(responseProperties)
    response
  }

  private def getRequestContext(request: Request): Context =
    HttpContext(
      transportContext = Some(request),
      method = Some(HttpMethod.valueOf(request.method.name)),
      headers = request.headers.map(header => header.headerName -> header.renderedValue).toSeq,
    ).url(request.url.toString)

  private def setResponseContext(response: Response, responseContext: Option[Context]): Response =
    response.updateHeaders(headers =>
      responseContext.map(
        _.headers.foldLeft(headers) { case (result, header) => result.combine(Headers(header)) }
      ).getOrElse(Headers.empty)
    )

  private def getRequestProperties(request: Request, requestId: String): Map[String, String] =
    ListMap(
      LogProperties.requestId -> requestId,
      LogProperties.client -> clientAddress(request),
      "URL" -> request.url.toString,
      "Method" -> request.method.name,
    )

  private def clientAddress(request: Request): String = {
    val forwardedFor = request.headers.find(_.headerName == headerXForwardedFor).map(_.renderedValue)
    val address = request.remoteAddress.map(_.toString).getOrElse("")
    Network.address(forwardedFor, address)
  }
}

object ZioHttpEndpoint {

  /** Request context type. */
  type Context = HttpContext[Request]

  private[automorph] val headerXForwardedFor = "X-Forwarded-For"
}
