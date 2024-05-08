package automorph.transport.server

import automorph.log.Logging
import automorph.spi.{EffectSystem, RequestHandler, ServerTransport}
import automorph.transport.HttpRequestHandler.{RequestData, ResponseData}
import automorph.transport.server.VertxHttpEndpoint.Context
import automorph.transport.{HttpContext, HttpMethod, HttpRequestHandler, Protocol}
import automorph.util.Extensions.EffectOps
import automorph.util.Network
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
 * @param handler
 *   RPC request handler
 * @tparam Effect
 *   effect type
 */
final case class VertxHttpEndpoint[Effect[_]](
  effectSystem: EffectSystem[Effect],
  mapException: Throwable => Int = HttpContext.toStatusCode,
  handler: RequestHandler[Effect, Context] = RequestHandler.dummy[Effect, Context],
) extends ServerTransport[Effect, Context, Handler[HttpServerRequest]] with Logging {
  private lazy val requestHandler = new Handler[HttpServerRequest] {

    override def handle(request: HttpServerRequest): Unit = {
      request.bodyHandler { buffer =>
        httpHandler.processRequest((request, buffer), request).runAsync
      }
      ()
    }
  }

  private val httpHandler =
    HttpRequestHandler(receiveRequest, sendResponse, Protocol.Http, effectSystem, mapException, handler, logger)
  implicit private val system: EffectSystem[Effect] = effectSystem

  override def adapter: Handler[HttpServerRequest] =
    requestHandler

  override def init(): Effect[Unit] =
    effectSystem.successful {}

  override def close(): Effect[Unit] =
    effectSystem.successful {}

  override def requestHandler(handler: RequestHandler[Effect, Context]): VertxHttpEndpoint[Effect] =
    copy(handler = handler)

  private def receiveRequest(
    incomingRequest: (HttpServerRequest, Buffer)
  ): (RequestData[Context], Effect[Array[Byte]]) = {
    val (request, body) = incomingRequest
    val requestData = RequestData(
      getRequestContext(request),
      httpHandler.protocol,
      request.absoluteURI,
      clientAddress(request),
      Some(request.method.name),
    )
    val requestBody = effectSystem.evaluate(body.getBytes)
    (requestData, requestBody)
  }

  private def sendResponse(responseData: ResponseData[Context], request: HttpServerRequest): Effect[Unit] =
    effectSystem.completable[Unit].flatMap { completable =>
      setResponseContext(request.response, responseData.context)
        .putHeader(HttpHeaders.CONTENT_TYPE, responseData.contentType)
        .setStatusCode(responseData.statusCode)
        .end(Buffer.buffer(responseData.body))
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
    ).url(request.absoluteURI)
  }

  private def setResponseContext(response: HttpServerResponse, responseContext: Option[Context]): HttpServerResponse =
    responseContext.toSeq.flatMap(_.headers).foldLeft(response) { case (current, (name, value)) =>
      current.putHeader(name, value)
    }

  private def clientAddress(request: HttpServerRequest): String =
    VertxHttpEndpoint.clientAddress(request.headers, request.remoteAddress)
}

object VertxHttpEndpoint {

  /** Request context type. */
  type Context = HttpContext[Either[HttpServerRequest, ServerWebSocket]]

  private[automorph] def clientAddress(headers: MultiMap, remoteAddress: SocketAddress): String = {
    val forwardedFor = Option(headers.get(HttpRequestHandler.headerXForwardedFor))
    val address = Option(remoteAddress.hostName).orElse(Option(remoteAddress.hostAddress)).getOrElse("")
    Network.address(forwardedFor, address)
  }
}
