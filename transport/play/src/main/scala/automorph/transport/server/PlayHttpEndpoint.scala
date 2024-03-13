package automorph.transport.server

import automorph.log.{LogProperties, Logging, MessageLog}
import automorph.spi.{EffectSystem, ServerTransport, RequestHandler}
import PlayHttpEndpoint.{Context, headerXForwardedFor}
import automorph.transport.{HttpContext, HttpMethod, Protocol}
import automorph.util.Extensions.{EffectOps, StringOps, ThrowableOps, TryOps}
import automorph.util.{Network, Random}
import org.apache.pekko.stream.Materializer
import org.apache.pekko.util.ByteString
import play.api.http.HttpEntity
import play.api.mvc.Results.{InternalServerError, Ok, Status}
import play.api.mvc.{Action, BodyParser, PlayBodyParsers, Request, Result}
import scala.collection.immutable.ListMap
import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util.Try

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

    override def apply(request: Request[ByteString]): Future[Result] = {
      // Log the request
      val requestId = Random.id
      lazy val requestProperties = getRequestProperties(request, requestId)
      log.receivedRequest(requestProperties)

      // Process the request
      Try {
        val requestBody = request.body.toArray
        runAsFuture {
          val handlerResult = handler.processRequest(requestBody, getRequestContext(request), requestId)
          handlerResult.either.map(
            _.fold(
              error => createErrorResponse(error, request, requestId, requestProperties),
              result => {
                // Send the response
                val responseBody = result.map(_.responseBody).getOrElse(Array.emptyByteArray)
                val status = result.flatMap(_.exception).map(mapException).map(Status.apply).getOrElse(Ok)
                createResponse(responseBody, status, result.flatMap(_.context), request, requestId)
              },
            )
          )
        }
      }.foldError { error =>
        Future.successful(createErrorResponse(error, request, requestId, requestProperties))
      }
    }

    override def executionContext: ExecutionContext =
      suppliedExecutionContext
  }
  private val suppliedExecutionContext = executionContext
  private val log = MessageLog(logger, Protocol.Http.name)
  implicit private val system: EffectSystem[Effect] = effectSystem

  override def endpoint: Action[ByteString] =
    action

  override def init(): Effect[Unit] =
    effectSystem.successful {}

  override def close(): Effect[Unit] =
    effectSystem.successful {}

  override def withHandler(handler: RequestHandler[Effect, Context]): PlayHttpEndpoint[Effect] =
    copy(handler = handler)

  private def createErrorResponse(
    error: Throwable,
    request: Request[ByteString],
    requestId: String,
    requestProperties: => Map[String, String],
  ): Result = {
    log.failedProcessRequest(error, requestProperties)
    val responseBody = error.trace.mkString("\n").toByteArray
    createResponse(responseBody, InternalServerError, None, request, requestId)
  }

  private def createResponse(
    responseBody: Array[Byte],
    status: Status,
    responseContext: Option[Context],
    request: Request[ByteString],
    requestId: String,
  ): Result = {
    // Log the response
    val responseStatus = responseContext.flatMap(_.statusCode.map(Status.apply)).getOrElse(status)
    lazy val responseProperties = ListMap(
      LogProperties.requestId -> requestId,
      LogProperties.client -> clientAddress(request),
      "Status" -> responseStatus.toString,
    )

    // Send the response
    val response = responseStatus.sendEntity(HttpEntity.Strict.apply(ByteString(responseBody), Some(handler.mediaType)))
    setResponseContext(response, responseContext)
    log.sendingResponse(responseProperties)
    response
  }

  private def getRequestContext(request: Request[ByteString]): Context =
    HttpContext(
      transportContext = Some(request),
      method = Some(HttpMethod.valueOf(request.method)),
      headers = request.headers.headers,
    ).url(request.uri)

  private def setResponseContext(response: Result, responseContext: Option[Context]): Result =
    response.withHeaders(responseContext.toSeq.flatMap(_.headers)*)

  private def getRequestProperties(request: Request[ByteString], requestId: String): Map[String, String] =
    ListMap(
      LogProperties.requestId -> requestId,
      LogProperties.client -> clientAddress(request),
      "URL" -> request.uri,
      "Method" -> request.method,
    )

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
