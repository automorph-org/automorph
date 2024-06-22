package automorph.transport.server

import automorph.spi.{EffectSystem, RpcHandler, ServerTransport}
import automorph.transport.HttpContext.headerRpcNodeId
import automorph.transport.server.FinagleHttpEndpoint.Context
import automorph.transport.{HttpContext, HttpMetadata, HttpMethod, Protocol, ServerHttpHandler}
import automorph.util.Extensions.EffectOps
import com.twitter.finagle.Service
import com.twitter.finagle.http.{Request, Response, Status}
import com.twitter.io.{Buf, Reader}
import com.twitter.util.{Future, Promise}

/**
 * Finagle HTTP endpoint message transport plugin.
 *
 * Interprets HTTP request body as a RPC request and processes it with the specified RPC handler.
 *   - The response returned by the RPC handler is used as HTTP response body.
 *
 * @see
 *   [[https://en.wikipedia.org/wiki/HTTP Transport protocol]]
 * @see
 *   [[https://twitter.github.io/finagle Library documentation]]
 * @see
 *   [[https://twitter.github.io/finagle/docs/com/twitter/finagle/ API]]
 * @constructor
 *   Creates an Finagle HTTP endpoint message transport plugin with specified effect system and request handler.
 * @param effectSystem
 *   effect system plugin
 * @param mapException
 *   maps an exception to a corresponding HTTP status code
 * @param handler
 *   RPC request handler
 * @tparam Effect
 *   effect type
 */
final case class FinagleHttpEndpoint[Effect[_]](
  effectSystem: EffectSystem[Effect],
  mapException: Throwable => Int = HttpContext.toStatusCode,
  handler: RpcHandler[Effect, Context] = RpcHandler.dummy[Effect, Context],
) extends ServerTransport[Effect, Context, Service[Request, Response]] {

  private lazy val service: Service[Request, Response] = (request: Request) =>
    runAsFuture(httpHandler.processRequest(request, request))
  private val httpHandler =
    ServerHttpHandler(receiveRequest, createResponse, Protocol.Http, effectSystem, mapException, handler)
  implicit private val system: EffectSystem[Effect] = effectSystem

  override def adapter: Service[Request, Response] =
    service

  override def init(): Effect[Unit] =
    effectSystem.successful {}

  override def close(): Effect[Unit] =
    effectSystem.successful {}

  override def requestHandler(handler: RpcHandler[Effect, Context]): FinagleHttpEndpoint[Effect] =
    copy(handler = handler)

  private def receiveRequest(request: Request): (Effect[Array[Byte]], HttpMetadata[Context]) = {
    val requestMetadata = HttpMetadata(
      getRequestContext(request),
      httpHandler.protocol,
      request.uri,
      Some(request.method.toString),
    )
    effectSystem.evaluate(Buf.ByteArray.Owned.extract(request.content)) -> requestMetadata
  }

  private def createResponse(body: Array[Byte], metadata: HttpMetadata[Context], request: Request): Effect[Response] = {
    val response = Response(
      request.version,
      Status(metadata.statusCodeOrOk),
      Reader.fromBuf(Buf.ByteArray.Owned(body)),
    )
    response.contentType = metadata.contentType
    setResponseContext(response, metadata.context)
    effectSystem.successful(response)
  }

  private def getRequestContext(request: Request): Context =
    HttpContext(
      transportContext = Some(request),
      method = Some(HttpMethod.valueOf(request.method.name)),
      headers = request.headerMap.iterator.toSeq,
      peer = Some(clientId(request)),
    ).url(request.uri)

  private def setResponseContext(response: Response, context: Context): Unit =
    context.headers.foreach { case (name, value) => response.headerMap.add(name, value) }

  private def clientId(request: Request): String = {
    val forwardedFor = request.xForwardedFor
    val nodeId = request.headerMap.get(headerRpcNodeId)
    val address = request.remoteAddress.toString
    ServerHttpHandler.client(address, forwardedFor, nodeId)
  }

  private def runAsFuture[T](value: => Effect[T]): Future[T] = {
    val promise = Promise[T]()
    value.either.map(_.fold(
      error => promise.setException(error),
      result => promise.setValue(result),
    )).runAsync
    promise
  }
}

object FinagleHttpEndpoint {

  /** Request context type. */
  type Context = HttpContext[Request]
}
