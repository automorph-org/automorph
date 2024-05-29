package automorph.transport.server

import automorph.spi.{EffectSystem, RequestHandler, ServerTransport}
import automorph.transport.HttpRequestHandler.{RequestMetadata, ResponseData, headerNodeId, headerXForwardedFor}
import automorph.transport.server.ZioHttpEndpoint.Context
import automorph.transport.{HttpContext, HttpMethod, HttpRequestHandler, Protocol}
import zio.http.{Body, Handler, Header, Headers, MediaType, Request, Response, Status}
import zio.{Chunk, IO, http}
import scala.annotation.unused

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
) extends ServerTransport[({ type Effect[A] = IO[Fault, A] })#Effect, Context, http.RequestHandler[Any, Response]] {

  private lazy val mediaType = MediaType.forContentType(handler.mediaType).getOrElse(
    throw new IllegalStateException(s"Invalid message content type: ${handler.mediaType}")
  )
  private lazy val requestHandler = Handler.fromFunctionZIO(handle)
  private val httpHandler =
    HttpRequestHandler(receiveRequest, createResponse, Protocol.Http, effectSystem, mapException, handler)

  override def adapter: http.RequestHandler[Any, Response] =
    requestHandler

  override def init(): IO[Fault, Unit] =
    effectSystem.successful {}

  override def close(): IO[Fault, Unit] =
    effectSystem.successful {}

  override def requestHandler(
    handler: RequestHandler[({ type Effect[A] = IO[Fault, A] })#Effect, Context]
  ): ZioHttpEndpoint[Fault] =
    copy(handler = handler)

  private def handle(request: Request): IO[Response, Response] =
    httpHandler.processRequest(request, ()).mapError(_ => Response())

  private def receiveRequest(request: Request): (RequestMetadata[Context], IO[Fault, Array[Byte]]) = {
    val requestMetadata = RequestMetadata(
      getRequestContext(request),
      httpHandler.protocol,
      request.url.toString,
      Some(request.method.name),
    )
    val requestBody = request.body.asArray.foldZIO(
      error => effectSystem.failed(error),
      body => effectSystem.successful(body),
    )
    (requestMetadata, requestBody)
  }

  private def createResponse(responseData: ResponseData[Context], @unused session: Unit): IO[Fault, Response] = {
    val response = setResponseContext(
      Response(
        Status.fromInt(responseData.statusCode).getOrElse(Status.Ok),
        Headers(Header.ContentType(mediaType)),
        Body.fromChunk(Chunk.fromArray(responseData.body)),
      ),
      responseData.context,
    )
    effectSystem.successful(response)
  }

  private def getRequestContext(request: Request): Context =
    HttpContext(
      transportContext = Some(request),
      method = Some(HttpMethod.valueOf(request.method.name)),
      headers = request.headers.map(header => header.headerName -> header.renderedValue).toSeq,
      peerId = Some(clientId(request)),
    ).url(request.url.toString)

  private def setResponseContext(response: Response, responseContext: Option[Context]): Response =
    response.updateHeaders(headers =>
      responseContext.map(
        _.headers.foldLeft(headers) { case (result, header) => result.combine(Headers(header)) }
      ).getOrElse(Headers.empty)
    )

  private def clientId(request: Request): String = {
    val address = request.remoteAddress.map(_.toString).getOrElse("")
    val forwardedFor = request.headers.find(_.headerName == headerXForwardedFor).map(_.renderedValue)
    val nodeId = request.headers.find(_.headerName == headerNodeId).map(_.renderedValue)
    HttpRequestHandler.clientId(address, forwardedFor, nodeId)
  }
}

object ZioHttpEndpoint {

  /** Request context type. */
  type Context = HttpContext[Request]
}
