package automorph.transport.server

import automorph.spi.{EffectSystem, RpcHandler, ServerTransport}
import automorph.transport.HttpContext.headerRpcNodeId
import automorph.transport.server.PekkoHttpEndpoint.Context
import automorph.transport.{HttpContext, HttpMetadata, HttpMethod, Protocol, ServerHttpHandler}
import automorph.util.Extensions.{EffectOps, ThrowableOps}
import org.apache.pekko.http.scaladsl.model.StatusCodes.InternalServerError
import org.apache.pekko.http.scaladsl.model.headers.RawHeader
import org.apache.pekko.http.scaladsl.model.{ContentType, HttpEntity, HttpRequest, HttpResponse, RemoteAddress, StatusCode}
import org.apache.pekko.http.scaladsl.server.Directives.{complete, extractClientIP, extractExecutionContext, extractMaterializer, extractRequest, onComplete}
import org.apache.pekko.http.scaladsl.server.Route
import org.apache.pekko.stream.Materializer
import scala.annotation.unused
import scala.concurrent.duration.{DurationInt, FiniteDuration}
import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.jdk.OptionConverters.RichOptional

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
 * @param rpcHandler
 *   RPC request handler
 * @tparam Effect
 *   effect type
 */
final case class PekkoHttpEndpoint[Effect[_]](
  effectSystem: EffectSystem[Effect],
  mapException: Throwable => Int = HttpContext.toStatusCode,
  readTimeout: FiniteDuration = 30.seconds,
  rpcHandler: RpcHandler[Effect, Context] = RpcHandler.dummy[Effect, Context],
) extends ServerTransport[Effect, Context, Route] {

  private lazy val contentType = ContentType.parse(rpcHandler.mediaType).swap.map { errors =>
    new IllegalStateException(s"Invalid message content type: ${errors.mkString("\n")}")
  }.swap.toTry.get
  private lazy val route: Route =
    extractRequest { httpRequest =>
      extractClientIP { remoteAddress =>
        extractMaterializer { implicit materializer =>
          extractExecutionContext { implicit executionContext =>
            onComplete(handleRequest(httpRequest, remoteAddress))(_.fold(
              error => complete(InternalServerError, error.description),
              response => complete(response),
            ))
          }
        }
      }
    }
  private val handler =
    ServerHttpHandler(receiveRequest, createResponse, Protocol.Http, effectSystem, mapException, rpcHandler)
  implicit private val system: EffectSystem[Effect] = effectSystem

  def adapter: Route =
    route

  override def init(): Effect[Unit] =
    effectSystem.successful {}

  override def close(): Effect[Unit] =
    effectSystem.successful {}

  override def requestHandler(handler: RpcHandler[Effect, Context]): PekkoHttpEndpoint[Effect] =
    copy(rpcHandler = handler)

  private def handleRequest(request: HttpRequest, remoteAddress: RemoteAddress)(
    implicit
    materializer: Materializer,
    executionContext: ExecutionContext,
  ): Future[HttpResponse] = {
    val handleRequestResult = Promise[HttpResponse]()
    request.entity.toStrict(readTimeout).flatMap { requestEntity =>
      handler.processRequest((request, requestEntity, remoteAddress), ()).fold(
        error => handleRequestResult.failure(error),
        result => handleRequestResult.success(result),
      )
      handleRequestResult.future
    }
  }

  private def receiveRequest(
    incomingRequest: (HttpRequest, HttpEntity.Strict, RemoteAddress)
  ): (Effect[Array[Byte]], Context) = {
    val (request, requestEntity, remoteAddress) = incomingRequest
    effectSystem.evaluate(requestEntity.data.toArray[Byte]) -> getRequestContext(request, remoteAddress)
  }

  private def createResponse(
    body: Array[Byte],
    metadata: HttpMetadata[Context],
    @unused channel: Unit,
  ): Effect[HttpResponse] =
    effectSystem.successful(
      createResponseContext(HttpResponse(), metadata.context)
        .withStatus(StatusCode.int2StatusCode(metadata.statusCodeOrOk))
        .withEntity(contentType, body)
    )

  private def getRequestContext(request: HttpRequest, remoteAddress: RemoteAddress): Context =
    HttpContext(
      transportContext = Some(request),
      method = Some(HttpMethod.valueOf(request.method.value)),
      headers = request.headers.map(header => header.name -> header.value),
      peer = Some(client(remoteAddress, request)),
    ).url(request.uri.toString)

  private def createResponseContext(response: HttpResponse, context: Context): HttpResponse =
    response.withHeaders(context.headers.map { case (name, value) => RawHeader(name, value) })

  private def client(remoteAddress: RemoteAddress, request: HttpRequest): String = {
    val address = remoteAddress.toOption.flatMap(address => Option(address.getHostAddress)).getOrElse("")
    val nodeId = request.getHeader(headerRpcNodeId).toScala.map(_.value)
    ServerHttpHandler.client(address, None, nodeId)
  }
}

object PekkoHttpEndpoint {

  /** Request context type. */
  type Context = HttpContext[HttpRequest]
}
