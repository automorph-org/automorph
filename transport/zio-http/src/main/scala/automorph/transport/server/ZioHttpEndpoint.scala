package automorph.transport.server

import automorph.spi.{EffectSystem, RpcHandler, ServerTransport}
import automorph.transport.HttpContext.headerRpcNodeId
import automorph.transport.HttpMetadata.headerXForwardedFor
import automorph.transport.server.ZioHttpEndpoint.Context
import automorph.transport.{HttpContext, HttpMetadata, HttpMethod, Protocol, ServerHttpHandler}
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
 * @param rpcHandler
 *   RPC request handler
 * @tparam Fault
 *   ZIO error type
 */
final case class ZioHttpEndpoint[Fault](
  effectSystem: EffectSystem[({ type Effect[A] = IO[Fault, A] })#Effect],
  mapException: Throwable => Int = HttpContext.toStatusCode,
  rpcHandler: RpcHandler[({ type Effect[A] = IO[Fault, A] })#Effect, Context] =
    RpcHandler.dummy[({ type Effect[A] = IO[Fault, A] })#Effect, Context],
) extends ServerTransport[({ type Effect[A] = IO[Fault, A] })#Effect, Context, http.RequestHandler[Any, Response]] {

  private lazy val mediaType = MediaType.forContentType(rpcHandler.mediaType).getOrElse(
    throw new IllegalStateException(s"Invalid message content type: ${rpcHandler.mediaType}")
  )
  private lazy val requestHandler = Handler.fromFunctionZIO(handle)
  private val handler =
    ServerHttpHandler(receiveRequest, createResponse, Protocol.Http, effectSystem, mapException, rpcHandler)

  override def adapter: http.RequestHandler[Any, Response] =
    requestHandler

  override def init(): IO[Fault, Unit] =
    effectSystem.successful {}

  override def close(): IO[Fault, Unit] =
    effectSystem.successful {}

  override def requestHandler(
    handler: RpcHandler[({ type Effect[A] = IO[Fault, A] })#Effect, Context]
  ): ZioHttpEndpoint[Fault] =
    copy(rpcHandler = handler)

  private def handle(request: Request): IO[Response, Response] =
    handler.processRequest(request, ()).mapError(_ => Response())

  private def receiveRequest(request: Request): (IO[Fault, Array[Byte]], Context) = {
    val requestBody = request.body.asArray.foldZIO(
      error => effectSystem.failed(error),
      body => effectSystem.successful(body),
    )
    requestBody -> getRequestContext(request)
  }

  private def createResponse(
    body: Array[Byte],
    metadata: HttpMetadata[Context],
    @unused session: Unit,
  ): IO[Fault, Response] = {
    val response = setResponseContext(
      Response(
        Status.fromInt(metadata.statusCodeOrOk),
        Headers(Header.ContentType(mediaType)),
        Body.fromChunk(Chunk.fromArray(body)),
      ),
      metadata.context,
    )
    effectSystem.successful(response)
  }

  private def getRequestContext(request: Request): Context =
    HttpContext(
      transportContext = Some(request),
      method = Some(HttpMethod.valueOf(request.method.name)),
      headers = request.headers.map(header => header.headerName -> header.renderedValue).toSeq,
      peer = Some(client(request)),
    ).url(request.url.toString)

  private def setResponseContext(response: Response, context: Context): Response =
    response.updateHeaders(headers =>
      context.headers.foldLeft(headers) { case (result, header) => result.combine(Headers(header)) }
    )

  private def client(request: Request): String = {
    val address = request.remoteAddress.map(_.toString).getOrElse("")
    val forwardedFor = request.headers.find(_.headerName == headerXForwardedFor).map(_.renderedValue)
    val nodeId = request.headers.find(_.headerName == headerRpcNodeId).map(_.renderedValue)
    ServerHttpHandler.client(address, forwardedFor, nodeId)
  }
}

object ZioHttpEndpoint {

  /** Request context type. */
  type Context = HttpContext[Request]
}
