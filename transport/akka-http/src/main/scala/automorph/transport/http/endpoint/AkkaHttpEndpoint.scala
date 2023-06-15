package automorph.transport.http.endpoint

import akka.http.scaladsl.model.StatusCodes.InternalServerError
import akka.http.scaladsl.model.headers.RawHeader
import akka.http.scaladsl.model.{ContentType, HttpRequest, HttpResponse, RemoteAddress, StatusCode, StatusCodes}
import akka.http.scaladsl.server.Directives.{
  complete, extractClientIP, extractExecutionContext, extractMaterializer, extractRequest, onComplete
}
import akka.http.scaladsl.server.Route
import akka.stream.Materializer
import automorph.log.{LogProperties, Logging, MessageLog}
import automorph.spi.{EffectSystem, EndpointTransport, RequestHandler}
import automorph.transport.http.endpoint.AkkaHttpEndpoint.Context
import automorph.transport.http.{HttpContext, HttpMethod, Protocol}
import automorph.util.Extensions.{EffectOps, StringOps, ThrowableOps, TryOps}
import automorph.util.{Network, Random}
import scala.collection.immutable.ListMap
import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.concurrent.duration.{DurationInt, FiniteDuration}
import scala.util.Try

/**
 * Akka HTTP endpoint message transport plugin.
 *
 * Interprets HTTP request body as a RPC request and processes it with the specified RPC handler.
 *   - The response returned by the RPC handler is used as HTTP response body.
 *
 * @see
 *   [[https://en.wikipedia.org/wiki/HTTP Transport protocol]]
 * @see
 *   [[https://doc.akka.io/docs/akka-http Library documentation]]
 * @see
 *   [[https://doc.akka.io/api/akka-http/current/akka/http/ API]]
 * @constructor
 *   Creates an Akka HTTP endpoint message transport plugin with specified effect system and request handler.
 * @param effectSystem
 *   effect system plugin
 * @param mapException
 *   maps an exception to a corresponding HTTP status code
 * @param readTimeout
 *   HTTP request read timeout
 * @param handler
 *   RPC request handler
 * @tparam Effect
 *   effect type
 */
final case class AkkaHttpEndpoint[Effect[_]](
  effectSystem: EffectSystem[Effect],
  mapException: Throwable => Int = HttpContext.defaultExceptionToStatusCode,
  readTimeout: FiniteDuration = 30.seconds,
  handler: RequestHandler[Effect, Context] = RequestHandler.dummy[Effect, Context],
) extends Logging with EndpointTransport[Effect, Context, Route] {

  private lazy val contentType = ContentType.parse(handler.mediaType).swap.map { errors =>
    new IllegalStateException(s"Invalid message content type: ${errors.mkString("\n")}")
  }.swap.toTry.get
  private val log = MessageLog(logger, Protocol.Http.name)
  private implicit val system: EffectSystem[Effect] = effectSystem

  def adapter: Route =
    extractRequest { httpRequest =>
      extractClientIP { remoteAddress =>
        extractMaterializer { implicit materializer =>
          extractExecutionContext { implicit executionContext =>
            onComplete(handleRequest(httpRequest, remoteAddress))(
              _.fold(
                error => {
                  log.failedProcessRequest(error, Map())
                  complete(InternalServerError, error.description)
                },
                { case (httpResponse, responseProperties) =>
                  log.sentResponse(responseProperties)
                  complete(httpResponse)
                },
              )
            )
          }
        }
      }
    }

  override def withHandler(handler: RequestHandler[Effect, Context]): AkkaHttpEndpoint[Effect] =
    copy(handler = handler)

  private def handleRequest(request: HttpRequest, remoteAddress: RemoteAddress)(
    implicit
    materializer: Materializer,
    executionContext: ExecutionContext
  ): Future[(HttpResponse, ListMap[String, String])] = {

    // Log the request
    val requestId = Random.id
    lazy val requestProperties = getRequestProperties(request, requestId, remoteAddress)
    log.receivedRequest(requestProperties)

    // Process the request
    val handleRequestResult = Promise[(HttpResponse, ListMap[String, String])]()
    request.entity.toStrict(readTimeout).flatMap { requestEntity =>
      Try {
        val requestBody = requestEntity.data.toArray[Byte]
        val response = handler.processRequest(requestBody, getRequestContext(request), requestId)
        response.either.map { processRequestResult =>
          val response = processRequestResult.fold(
            error => createErrorResponse(error, contentType, remoteAddress, requestId, requestProperties),
            result => {
              // Create the response
              val responseBody = result.map(_.responseBody).getOrElse(Array.emptyByteArray)
              val status = result.flatMap(_.exception).map(mapException).map(StatusCode.int2StatusCode)
                .getOrElse(StatusCodes.OK)
              createResponse(responseBody, status, contentType, result.flatMap(_.context), remoteAddress, requestId)
            },
          )
          handleRequestResult.success(response)
        }.runAsync
      }.foldError { error =>
        handleRequestResult.success(
          createErrorResponse(error, contentType, remoteAddress, requestId, requestProperties)
        )
      }
      handleRequestResult.future
    }
  }

  private def createErrorResponse(
    error: Throwable,
    contentType: ContentType,
    remoteAddress: RemoteAddress,
    requestId: String,
    requestProperties: => Map[String, String],
  ): (HttpResponse, ListMap[String, String]) = {
    log.failedProcessRequest(error, requestProperties)
    val responseBody = error.description.toByteArray
    createResponse(responseBody, StatusCodes.InternalServerError, contentType, None, remoteAddress, requestId)
  }

  private def createResponse(
    responseBody: Array[Byte],
    statusCode: StatusCode,
    contentType: ContentType,
    responseContext: Option[Context],
    remoteAddress: RemoteAddress,
    requestId: String,
  ): (HttpResponse, ListMap[String, String]) = {
    // Log the response
    val responseStatusCode = responseContext.flatMap(_.statusCode.map(StatusCode.int2StatusCode)).getOrElse(statusCode)
    lazy val responseProperties = ListMap(
      LogProperties.requestId -> requestId,
      LogProperties.client -> clientAddress(remoteAddress),
      "Status" -> responseStatusCode.intValue.toString,
    )
    log.sendingResponse(responseProperties)

    // Send the response
    val baseResponse = setResponseContext(HttpResponse(), responseContext)
    val response = baseResponse.withStatus(responseStatusCode).withHeaders(baseResponse.headers)
      .withEntity(contentType, responseBody)
    response -> responseProperties
  }

  private def setResponseContext(response: HttpResponse, responseContext: Option[Context]): HttpResponse =
    response.withHeaders(responseContext.toSeq.flatMap(_.headers).map { case (name, value) => RawHeader(name, value) })

  private def getRequestContext(request: HttpRequest): Context =
    HttpContext(
      transportContext = Some(request),
      method = Some(HttpMethod.valueOf(request.method.value)),
      headers = request.headers.map(header => header.name -> header.value),
    ).url(request.uri.toString)

  private def getRequestProperties(
    request: HttpRequest,
    requestId: String,
    remoteAddress: RemoteAddress,
  ): Map[String, String] =
    ListMap(
      LogProperties.requestId -> requestId,
      LogProperties.client -> clientAddress(remoteAddress),
      "URL" -> request.uri.toString,
      "Method" -> request.method.value,
    )

  private def clientAddress(remoteAddress: RemoteAddress): String =
    remoteAddress.toOption.flatMap(address => Option(address.getHostAddress).map(Network.address(None, _)))
      .getOrElse("")
}

case object AkkaHttpEndpoint extends Logging {

  /** Request context type. */
  type Context = HttpContext[HttpRequest]
}
