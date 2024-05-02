package automorph.transport.server

import automorph.log.{LogProperties, Logging, MessageLog}
import automorph.spi.{EffectSystem, RequestHandler, ServerTransport}
import automorph.transport.HttpRequestHandler.{RequestData, ResponseData}
import automorph.transport.server.PekkoHttpEndpoint.Context
import automorph.transport.{HttpContext, HttpMethod, HttpRequestHandler, Protocol}
import automorph.util.Extensions.{EffectOps, ThrowableOps}
import automorph.util.Network
import org.apache.pekko.http.scaladsl.model.StatusCodes.InternalServerError
import org.apache.pekko.http.scaladsl.model.headers.RawHeader
import org.apache.pekko.http.scaladsl.model.{
  ContentType, HttpEntity, HttpRequest, HttpResponse, RemoteAddress, StatusCode,
}
import org.apache.pekko.http.scaladsl.server.Directives.{
  complete, extractClientIP, extractExecutionContext, extractMaterializer, extractRequest, onComplete,
}
import org.apache.pekko.http.scaladsl.server.Route
import org.apache.pekko.stream.Materializer
import scala.annotation.unused
import scala.collection.immutable.ListMap
import scala.concurrent.duration.{DurationInt, FiniteDuration}
import scala.concurrent.{ExecutionContext, Future, Promise}

/**
 * Pekko HTTP endpoint message transport plugin.
 *
 * Interprets HTTP request body as a RPC request and processes it with the specified RPC handler.
 *   - The response returned by the RPC handler is used as HTTP response body.
 *
 * @see
 *   [[https://en.wikipedia.org/wiki/HTTP Transport protocol]]
 * @see
 *   [[https://pekko.apache.org Library documentation]]
 * @see
 *   [[https://pekko.apache.org/api/pekko-http/current/ API]]
 * @constructor
 *   Creates an Pekko HTTP endpoint message transport plugin with specified effect system and request handler.
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
final case class PekkoHttpEndpoint[Effect[_]](
  effectSystem: EffectSystem[Effect],
  mapException: Throwable => Int = HttpContext.toStatusCode,
  readTimeout: FiniteDuration = 30.seconds,
  handler: RequestHandler[Effect, Context] = RequestHandler.dummy[Effect, Context],
) extends ServerTransport[Effect, Context, Route] with Logging {

  private lazy val contentType = ContentType.parse(handler.mediaType).swap.map { errors =>
    new IllegalStateException(s"Invalid message content type: ${errors.mkString("\n")}")
  }.swap.toTry.get
  private lazy val route: Route =
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
                { case (httpResponse, responseData) =>
                  log.sentResponse(ListMap(
                    LogProperties.requestId -> responseData.id,
                    LogProperties.client -> responseData.client,
                    LogProperties.status -> responseData.statusCode.toString,
                  ))
                  complete(httpResponse)
                },
              )
            )
          }
        }
      }
    }
  private val httpRequestHandler =
    HttpRequestHandler(receiveRequest, createResponse, Protocol.Http, effectSystem, mapException, handler, logger)
  private val log = MessageLog(logger, Protocol.Http.name)
  implicit private val system: EffectSystem[Effect] = effectSystem

  def adapter: Route =
    route

  override def init(): Effect[Unit] =
    effectSystem.successful {}

  override def close(): Effect[Unit] =
    effectSystem.successful {}

  override def requestHandler(handler: RequestHandler[Effect, Context]): PekkoHttpEndpoint[Effect] =
    copy(handler = handler)

  private def handleRequest(request: HttpRequest, remoteAddress: RemoteAddress)(
    implicit
    materializer: Materializer,
    executionContext: ExecutionContext,
  ): Future[(HttpResponse, ResponseData[Context])] = {
    val handleRequestResult = Promise[(HttpResponse, ResponseData[Context])]()
    request.entity.toStrict(readTimeout).flatMap { requestEntity =>
      httpRequestHandler.processRequest((request, requestEntity, remoteAddress), ()).either.map(_.fold(
        error => handleRequestResult.failure(error),
        result => handleRequestResult.success(result),
      ))
      handleRequestResult.future
    }
  }

  private def receiveRequest(incomingRequest: (HttpRequest, HttpEntity.Strict, RemoteAddress)): RequestData[Context] = {
    val (request, requestEntity, remoteAddress) = incomingRequest
    RequestData(
      () => requestEntity.data.toArray[Byte],
      getRequestContext(request),
      Protocol.Http,
      request.uri.toString,
      clientAddress(remoteAddress),
      Some(request.method.value),
    )
  }

  private def createResponse(
    responseData: ResponseData[Context],
    @unused channel: Unit,
  ): (HttpResponse, ResponseData[Context]) = {
    val response = createResponseContext(HttpResponse(), responseData.context)
      .withStatus(StatusCode.int2StatusCode(responseData.statusCode))
      .withEntity(contentType, responseData.body)
    (response, responseData)
  }

  private def getRequestContext(request: HttpRequest): Context =
    HttpContext(
      transportContext = Some(request),
      method = Some(HttpMethod.valueOf(request.method.value)),
      headers = request.headers.map(header => header.name -> header.value),
    ).url(request.uri.toString)

  private def createResponseContext(response: HttpResponse, responseContext: Option[Context]): HttpResponse =
    response.withHeaders(responseContext.toSeq.flatMap(_.headers).map { case (name, value) => RawHeader(name, value) })

  private def clientAddress(remoteAddress: RemoteAddress): String =
    remoteAddress.toOption
      .flatMap(address => Option(address.getHostAddress).map(Network.address(None, _))).getOrElse("")
}

object PekkoHttpEndpoint extends Logging {

  /** Request context type. */
  type Context = HttpContext[HttpRequest]
}
