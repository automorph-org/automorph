package automorph.transport.server

import automorph.log.Logging
import automorph.spi.{EffectSystem, RequestHandler, ServerTransport}
import automorph.transport.HttpRequestHandler.{RequestData, ResponseData}
import automorph.transport.server.PlayHttpEndpoint.{Context, headerXForwardedFor}
import automorph.transport.{HttpContext, HttpMethod, HttpRequestHandler, Protocol}
import automorph.util.Extensions.EffectOps
import automorph.util.Network
import org.apache.pekko.stream.Materializer
import org.apache.pekko.util.ByteString
import play.api.http.HttpEntity
import play.api.mvc.Results.Status
import play.api.mvc.{Action, BodyParser, PlayBodyParsers, Request, Result}
import scala.annotation.unused
import scala.concurrent.{ExecutionContext, Future, Promise}

/**
 * Play HTTP endpoint message transport plugin.
 *
 * Interprets HTTP request body as a RPC request and processes it with the specified RPC handler.
 *   - The response returned by the RPC handler is used as HTTP response body.
 *
 * @see
 *   [[https://en.wikipedia.org/wiki/HTTP Transport protocol]]
 * @see
 *   [[https://www.playframework.com/documentation/latest/Home Library documentation]]
 * @see
 *   [[https://www.playframework.com/documentation/latest/api/scala/index.html API]]
 * @constructor
 *   Creates an Play HTTP endpoint message transport plugin with specified effect system and request handler.
 * @param effectSystem
 *   effect system plugin
 * @param mapException
 *   maps an exception to a corresponding HTTP status code
 * @param handler
 *   RPC request handler
 * @param executionContext
 *   execution context
 * @param materializer
 *   Pekko stream materializer
 * @tparam Effect
 *   effect type
 */
final case class PlayHttpEndpoint[Effect[_]](
  effectSystem: EffectSystem[Effect],
  mapException: Throwable => Int = HttpContext.toStatusCode,
  handler: RequestHandler[Effect, Context] = RequestHandler.dummy[Effect, Context],
)(implicit val executionContext: ExecutionContext, val materializer: Materializer)
  extends ServerTransport[Effect, Context, Action[ByteString]] with Logging {

  private lazy val action = new Action[ByteString] {

    override def parser: BodyParser[ByteString] =
      PlayBodyParsers().byteString

    override def apply(request: Request[ByteString]): Future[Result] =
      runAsFuture {
        httpHandler.processRequest(request, ())
      }

    override def executionContext: ExecutionContext =
      suppliedExecutionContext
  }
  private val suppliedExecutionContext = executionContext
  private val httpHandler =
    HttpRequestHandler(receiveRequest, createResponse, Protocol.Http, effectSystem, mapException, handler, logger)
  implicit private val system: EffectSystem[Effect] = effectSystem

  override def adapter: Action[ByteString] =
    action

  override def init(): Effect[Unit] =
    effectSystem.successful {}

  override def close(): Effect[Unit] =
    effectSystem.successful {}

  override def requestHandler(handler: RequestHandler[Effect, Context]): PlayHttpEndpoint[Effect] =
    copy(handler = handler)

  private def receiveRequest(request: Request[ByteString]): RequestData[Context] =
    RequestData(
      () => request.body.toArray,
      getRequestContext(request),
      httpHandler.protocol,
      request.uri,
      clientAddress(request),
      Some(request.method),
    )

  private def createResponse(
    responseData: ResponseData[Context],
    @unused session: Unit,
    @unused logResponse: Option[Throwable] => Unit,
  ): Effect[Result] = {
    val httpEntity = HttpEntity.Strict.apply(ByteString(responseData.body), Some(handler.mediaType))
    val result = setResponseContext(Status(responseData.statusCode).sendEntity(httpEntity), responseData.context)
    effectSystem.successful(result)
  }

  private def getRequestContext(request: Request[ByteString]): Context =
    HttpContext(
      transportContext = Some(request),
      method = Some(HttpMethod.valueOf(request.method)),
      headers = request.headers.headers,
    ).url(request.uri)

  private def setResponseContext(response: Result, responseContext: Option[Context]): Result =
    response.withHeaders(responseContext.toSeq.flatMap(_.headers)*)

  private def clientAddress(request: Request[ByteString]): String = {
    val forwardedFor = request.headers.headers.find(_._1 == headerXForwardedFor).map(_._2)
    val address = request.remoteAddress
    Network.address(forwardedFor, address)
  }

  private def runAsFuture[T](value: => Effect[T]): Future[T] = {
    val promise = Promise[T]()
    value.either.map(_.fold(error => promise.failure(error), result => promise.success(result))).runAsync
    promise.future
  }
}

object PlayHttpEndpoint {

  /** Request context type. */
  type Context = HttpContext[Request[ByteString]]

  private[automorph] val headerXForwardedFor = "X-Forwarded-For"
}
