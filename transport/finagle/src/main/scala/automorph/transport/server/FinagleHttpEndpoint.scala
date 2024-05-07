package automorph.transport.server

import automorph.log.Logging
import automorph.spi.{EffectSystem, RequestHandler, ServerTransport}
import automorph.transport.HttpRequestHandler.{RequestData, ResponseData}
import automorph.transport.server.FinagleHttpEndpoint.Context
import automorph.transport.{HttpContext, HttpMethod, HttpRequestHandler, Protocol}
import automorph.util.Extensions.EffectOps
import automorph.util.Network
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
  handler: RequestHandler[Effect, Context] = RequestHandler.dummy[Effect, Context],
) extends ServerTransport[Effect, Context, Service[Request, Response]] with Logging {

  private lazy val service: Service[Request, Response] = (request: Request) =>
    runAsFuture(httpHandler.processRequest(request, request))
  private val httpHandler =
    HttpRequestHandler(receiveRequest, createResponse, Protocol.Http, effectSystem, mapException, handler, logger)
  implicit private val system: EffectSystem[Effect] = effectSystem

  override def adapter: Service[Request, Response] =
    service

  override def init(): Effect[Unit] =
    effectSystem.successful {}

  override def close(): Effect[Unit] =
    effectSystem.successful {}

  override def requestHandler(handler: RequestHandler[Effect, Context]): FinagleHttpEndpoint[Effect] =
    copy(handler = handler)

  private def receiveRequest(request: Request): RequestData[Context] =
    RequestData(
      () => Buf.ByteArray.Owned.extract(request.content),
      getRequestContext(request),
      httpHandler.protocol,
      request.uri,
      clientAddress(request),
      Some(request.method.toString),
    )

  private def createResponse(responseData: ResponseData[Context], request: Request): Effect[Response] = {
    val response = Response(
      request.version,
      Status(responseData.statusCode),
      Reader.fromBuf(Buf.ByteArray.Owned(responseData.body)),
    )
    response.contentType = responseData.contentType
    setResponseContext(response, responseData.context)
    effectSystem.successful(response)
  }

  private def getRequestContext(request: Request): Context =
    HttpContext(
      transportContext = Some(request),
      method = Some(HttpMethod.valueOf(request.method.name)),
      headers = request.headerMap.iterator.toSeq,
    ).url(request.uri)

  private def setResponseContext(response: Response, responseContext: Option[Context]): Unit =
    responseContext.toSeq.flatMap(_.headers).foreach { case (name, value) => response.headerMap.add(name, value) }

  private def clientAddress(request: Request): String = {
    val forwardedFor = request.xForwardedFor
    val address = request.remoteAddress.toString
    Network.address(forwardedFor, address)
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
