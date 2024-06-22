package automorph.transport.server

import automorph.spi.{EffectSystem, RpcHandler, ServerTransport}
import automorph.transport.HttpContext.headerRpcNodeId
import automorph.transport.HttpMetadata.headerXForwardedFor
import automorph.transport.server.TapirHttpEndpoint.{
  Adapter, Context, MessageFormat, createResponse, pathComponents, pathEndpointInput, receiveRequest,
}
import automorph.transport.{HttpContext, HttpMetadata, HttpMethod, Protocol, ServerHttpHandler}
import automorph.util.Extensions.EffectOps
import sttp.model.{Header, MediaType, Method, QueryParams, StatusCode}
import sttp.tapir
import sttp.tapir.Codec.id
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.{
  CodecFormat, EndpointIO, EndpointInput, RawBodyType, Schema, headers, paths, queryParams, statusCode, stringToPath,
}
import scala.annotation.unused

/**
 * Tapir HTTP endpoint message transport plugin.
 *
 * Interprets HTTP request body as an RPC request and processes it using the specified RPC request handler.
 *   - The response returned by the RPC request handler is used as HTTP response body.
 *
 * @see
 *   [[https://en.wikipedia.org/wiki/HTTP Transport protocol]]
 * @see
 *   [[https://tapir.softwaremill.com Library documentation]]
 * @see
 *   [[https://javadoc.io/doc/com.softwaremill.sttp.tapir/tapir-core_3/latest/index.html API]]
 * @constructor
 *   Creates a Tapir HTTP endpoint message transport plugin with specified effect system and request handler.
 * @param effectSystem
 *   effect system plugin
 * @param pathPrefix
 *   HTTP URL path prefix, only requests starting with this path prefix are allowed
 * @param method
 *   allowed HTTP method, all methods are allowed if empty
 * @param baseUrl
 *   base server URL, recommended to set since Tapir does not provide full request URLs
 * @param mapException
 *   maps an exception to a corresponding HTTP status code
 * @param handler
 *   RPC request handler
 * @tparam Effect
 *   effect type
 */
final case class TapirHttpEndpoint[Effect[_]](
  effectSystem: EffectSystem[Effect],
  pathPrefix: String = "/",
  method: Option[HttpMethod] = None,
  baseUrl: String = "http://localhost",
  mapException: Throwable => Int = HttpContext.toStatusCode,
  handler: RpcHandler[Effect, Context] = RpcHandler.dummy[Effect, Context],
) extends ServerTransport[Effect, Context, Adapter[Effect]] {

  private lazy val mediaType = MediaType.parse(handler.mediaType).fold(
    error =>
      throw new IllegalStateException(
        s"Invalid message content type: ${handler.mediaType}",
        new IllegalArgumentException(error),
      ),
    identity,
  )
  private lazy val codec = id[Array[Byte], MessageFormat](MessageFormat(mediaType), Schema.schemaForByteArray)
  private lazy val body = EndpointIO.Body(RawBodyType.ByteArrayBody, codec, EndpointIO.Info.empty)
  private val allowedMethod = method.map(httpMethod => Method(httpMethod.name))
  private val prefixPaths = pathComponents(pathPrefix)
  private val baseContext = HttpContext[Unit]().url(baseUrl)
  private val httpHandler = ServerHttpHandler(
    receiveRequest(effectSystem),
    createResponse(effectSystem),
    Protocol.Http,
    effectSystem,
    mapException,
    handler,
  )
  implicit private val system: EffectSystem[Effect] = effectSystem

  override def adapter: Adapter[Effect] = {
    // Define server endpoint inputs & outputs
    val endpointMethod = allowedMethod.map(tapir.endpoint.method).getOrElse(tapir.endpoint)
    val endpointPath = pathEndpointInput(prefixPaths).map(path => endpointMethod.in(path)).getOrElse(endpointMethod)
    val endpointInput = endpointPath.in(body).in(paths).in(queryParams).in(headers)
    val endpointOutput = endpointInput.out(body).out(statusCode).out(headers)

    // Define server endpoint request processing logic
    endpointOutput.serverLogic { request =>
      val fullRequest = request.copy(_2 = prefixPaths ++ request._2)
      httpHandler.processRequest((fullRequest, method, baseContext), ()).map(Right.apply)
    }
  }

  override def init(): Effect[Unit] =
    effectSystem.successful {}

  override def close(): Effect[Unit] =
    effectSystem.successful {}

  override def requestHandler(handler: RpcHandler[Effect, Context]): TapirHttpEndpoint[Effect] =
    copy(handler = handler)
}

object TapirHttpEndpoint {

  /** Request context type. */
  type Context = HttpContext[Unit]

  /** Adapter type. */
  type Adapter[Effect[_]] = ServerEndpoint.Full[Unit, Unit, Request, Unit, Response, Any, Effect]

  private type Request = (Array[Byte], List[String], QueryParams, List[Header])

  private type Response = (Array[Byte], StatusCode, List[Header])

  private val leadingSlashPattern = "^/+".r
  private val trailingSlashPattern = "/+$".r
  private val multiSlashPattern = "/+".r

  private def receiveRequest[Effect[_]](effectSystem: EffectSystem[Effect])(
    incomingRequest: (Request, Option[HttpMethod], HttpContext[Unit])
  ): (Effect[Array[Byte]], HttpMetadata[Context]) = {
    val (request, method, baseContext) = incomingRequest
    val context = getRequestContext(request, method, baseContext)
    val url = context.url.map(_.toString).getOrElse("")
    val requestMetadata = HttpMetadata(context, Protocol.Http, url, method.map(_.name))
    effectSystem.successful(request._1) -> requestMetadata
  }

  private def createResponse[Effect[_]](effectSystem: EffectSystem[Effect])(
    body: Array[Byte],
    metadata: HttpMetadata[Context],
    @unused connection: Unit,
  ): Effect[Response] =
    effectSystem.successful(
      (body, StatusCode(metadata.statusCodeOrOk), setResponseContext(metadata))
    )

  private def getRequestContext(
    request: Request,
    method: Option[HttpMethod],
    baseContext: HttpContext[Unit],
  ): Context = {
    val (_, paths, queryParams, headers) = request
    val context = baseContext
      .path(urlPath(paths))
      .parameters(queryParams.toSeq*)
      .headers(headers.map(header => header.name -> header.value)*)
      .peerId(client(request))
    method.map(context.method(_)).getOrElse(context)
  }

  private def setResponseContext(response: HttpMetadata[Context]): List[Header] =
    response.context.headers.map { case (name, value) =>
      Header(name, value)
    }.toList

  private def client(request: Request): String = {
    val (_, _, _, headers) = request
    val forwardedFor = headers.find(_.name == headerXForwardedFor).map(_.value)
    val nodeId = headers.find(_.name == headerRpcNodeId).map(_.value)
    ServerHttpHandler.client("", forwardedFor, nodeId)
  }

  private def urlPath(paths: List[String]): String =
    s"/${paths.mkString("/")}"

  private def pathComponents(path: String): List[String] = {
    val canonicalPath = multiSlashPattern.replaceAllIn(
      trailingSlashPattern.replaceAllIn(leadingSlashPattern.replaceAllIn(path, ""), ""),
      "/",
    )
    canonicalPath.split("/") match {
      case Array(head) if head.isEmpty => List.empty
      case components => components.toList
    }
  }

  private def pathEndpointInput(pathComponents: List[String]): Option[EndpointInput[Unit]] =
    pathComponents match {
      case Nil => None
      case head :: tail =>
        Some(tail.foldLeft[EndpointInput[Unit]](stringToPath(head)) { case (current, next) =>
          current.and(stringToPath(next))
        })
    }

  final private case class MessageFormat(mediaType: MediaType) extends CodecFormat
}
