package automorph.transport.server

import automorph.spi.{EffectSystem, RpcHandler, ServerTransport}
import automorph.transport.HttpContext.headerRpcNodeId
import automorph.transport.HttpMetadata.headerXForwardedFor
import automorph.transport.server.VertxHttpEndpoint.Context
import automorph.transport.{ClientServerHttpHandler, HttpContext, HttpMetadata, HttpMethod, Protocol, ServerHttpHandler}
import automorph.util.Extensions.EffectOps
import io.vertx.core.buffer.Buffer
import io.vertx.core.http.{HttpHeaders, HttpServerRequest, HttpServerResponse, ServerWebSocket}
import io.vertx.core.net.SocketAddress
import io.vertx.core.{Handler, MultiMap}
import scala.jdk.CollectionConverters.ListHasAsScala

/**
 * Vert.x HTTP endpoint message transport plugin.
 *
 * Interprets HTTP request body as an RPC request and processes it using the specified RPC request handler.
 *   - The response returned by the RPC request handler is used as HTTP response body.
 *
 * @see
 *   [[https://en.wikipedia.org/wiki/Hypertext Transport protocol]]
 * @see
 *   [[https://vertx.io Library documentation]]
 * @see
 *   [[https://vertx.io/docs/apidocs/index.html API]]
 * @constructor
 *   Creates a Vert.x HTTP endpoint message transport plugin with specified effect system and request handler.
 * @param effectSystem
 *   effect system plugin
 * @param mapException
 *   maps an exception to a corresponding HTTP status code
 * @param rpcHandler
 *   RPC request handler
 * @tparam Effect
 *   effect type
 */
final case class VertxHttpEndpoint[Effect[_]](
  effectSystem: EffectSystem[Effect],
  mapException: Throwable => Int = HttpContext.toStatusCode,
  rpcHandler: RpcHandler[Effect, Context] = RpcHandler.dummy[Effect, Context],
) extends ServerTransport[Effect, Context, Handler[HttpServerRequest]] {

  private lazy val requestHandler = new Handler[HttpServerRequest] {

    override def handle(request: HttpServerRequest): Unit = {
      request.bodyHandler { buffer =>
        handler.processRequest((request, buffer), request).runAsync
      }
      ()
    }
  }

  private val handler =
    ClientServerHttpHandler(receiveRequest, sendResponse, Protocol.Http, effectSystem, mapException, rpcHandler)
  implicit private val system: EffectSystem[Effect] = effectSystem

  override def adapter: Handler[HttpServerRequest] =
    requestHandler

  override def init(): Effect[Unit] =
    effectSystem.successful {}

  override def close(): Effect[Unit] =
    effectSystem.successful {}

  override def requestHandler(handler: RpcHandler[Effect, Context]): VertxHttpEndpoint[Effect] =
    copy(rpcHandler = handler)

  private def receiveRequest(incomingRequest: (HttpServerRequest, Buffer)): (Effect[Array[Byte]], Context) = {
    val (request, body) = incomingRequest
    effectSystem.evaluate(body.getBytes) -> getRequestContext(request)
  }

  private def sendResponse(
    body: Array[Byte],
    metadata: HttpMetadata[Context],
    request: HttpServerRequest,
  ): Effect[Unit] =
    effectSystem.completable[Unit].flatMap { completable =>
      setResponseContext(request.response, metadata.context)
        .putHeader(HttpHeaders.CONTENT_TYPE, metadata.contentType)
        .setStatusCode(metadata.statusCodeOrOk)
        .end(Buffer.buffer(body))
        .onSuccess(_ => completable.succeed(()).runAsync)
        .onFailure(error => completable.fail(error).runAsync)
      completable.effect
    }

  private def getRequestContext(request: HttpServerRequest): Context = {
    val headers = request.headers.entries.asScala.map(entry => entry.getKey -> entry.getValue).toSeq
    HttpContext(
      transportContext = Some(Left(request).withRight[ServerWebSocket]),
      method = Some(HttpMethod.valueOf(request.method.name)),
      headers = headers,
      peer = Some(client(request)),
    ).url(request.absoluteURI)
  }

  private def setResponseContext(response: HttpServerResponse, context: Context): HttpServerResponse =
    context.headers.foldLeft(response) { case (current, (name, value)) =>
      current.putHeader(name, value)
    }

  private def client(request: HttpServerRequest): String =
    VertxHttpEndpoint.clientId(request.headers, request.remoteAddress)
}

object VertxHttpEndpoint {

  /** Request context type. */
  type Context = HttpContext[Either[HttpServerRequest, ServerWebSocket]]

  private[automorph] def clientId(headers: MultiMap, remoteAddress: SocketAddress): String = {
    val address = Option(remoteAddress.hostName).orElse(Option(remoteAddress.hostAddress)).getOrElse("")
    val forwardedFor = Option(headers.get(headerXForwardedFor))
    val nodeId = Option(headers.get(headerRpcNodeId))
    ServerHttpHandler.client(address, forwardedFor, nodeId)
  }
}
