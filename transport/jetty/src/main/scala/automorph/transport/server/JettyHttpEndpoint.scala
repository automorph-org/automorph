package automorph.transport.server

import automorph.spi.{EffectSystem, RpcHandler, ServerTransport}
import automorph.transport.HttpClientBase.completableEffect
import automorph.transport.HttpContext.headerRpcNodeId
import automorph.transport.server.JettyHttpEndpoint.Context
import automorph.transport.{ClientServerHttpHandler, HttpContext, HttpMetadata, HttpMethod, Protocol, ServerHttpHandler}
import automorph.util.Extensions.{ByteArrayOps, ByteBufferOps, EffectOps}
import org.eclipse.jetty.http.HttpHeader
import org.eclipse.jetty.io.Content
import org.eclipse.jetty.server.{Handler, Request, Response}
import org.eclipse.jetty.util.Callback
import scala.jdk.CollectionConverters.{EnumerationHasAsScala, SetHasAsScala}

/**
 * Jetty HTTP endpoint message transport plugin.
 *
 * Interprets HTTP request body as an RPC request and processes it with the specified RPC handler.
 *   - The response returned by the RPC handler is used as HTTP response body.
 *
 * @see
 *   [[https://en.wikipedia.org/wiki/HTTP Transport protocol]]
 * @see
 *   [[https://www.eclipse.org/jetty Library documentation]]
 * @see
 *   [[https://www.eclipse.org/jetty/javadoc/jetty-11/index.html API]]
 * @constructor
 *   Creates an Jetty HTTP endpoint message transport plugin with specified effect system and request handler.
 * @param effectSystem
 *   effect system plugin
 * @param mapException
 *   maps an exception to a corresponding HTTP status code
 * @param rpcHandler
 *   RPC request handler
 * @tparam Effect
 *   effect type
 */
final case class JettyHttpEndpoint[Effect[_]](
  effectSystem: EffectSystem[Effect],
  mapException: Throwable => Int = HttpContext.toStatusCode,
  rpcHandler: RpcHandler[Effect, Context] = RpcHandler.dummy[Effect, Context],
) extends ServerTransport[Effect, Context, Handler] {
  private lazy val httpHandler = new Handler.Abstract() {
    implicit private val system: EffectSystem[Effect] = effectSystem

    override def handle(request: Request, response: Response, callback: Callback): Boolean = {
      handler.processRequest(request, response).fold(callback.failed, _ => callback.succeeded()).runAsync
      true
    }
  }
  private val handler =
    ClientServerHttpHandler(receiveRequest, sendResponse, Protocol.Http, effectSystem, mapException, rpcHandler)
  implicit private val system: EffectSystem[Effect] = effectSystem

  override def adapter: Handler =
    httpHandler

  override def init(): Effect[Unit] =
    effectSystem.successful {}

  override def close(): Effect[Unit] =
    effectSystem.successful {}

  override def rpcHandler(handler: RpcHandler[Effect, Context]): JettyHttpEndpoint[Effect] =
    copy(rpcHandler = handler)

  private def receiveRequest(request: Request): (Effect[Array[Byte]], Context) = {
    val requestBody = completableEffect(Content.Source.asByteBufferAsync(request), system).map(_.toByteArray)
    requestBody -> getRequestContext(request)
  }

  private def sendResponse(
    body: Array[Byte],
    metadata: HttpMetadata[Context],
    response: Response,
  ): Effect[Unit] = {
    setResponseContext(response, metadata.context)
    response.getHeaders.put(HttpHeader.CONTENT_TYPE, metadata.contentType)
    response.setStatus(metadata.statusCodeOrOk)
    effectSystem.completable[Unit].map { expectedSent =>
      response.write(
        true,
        body.toByteBuffer,
        new Callback {
          override def succeeded(): Unit =
            expectedSent.succeed(()).runAsync

          override def failed(error: Throwable): Unit =
            expectedSent.fail(error).runAsync
        },
      )
    }
  }

  private def setResponseContext(response: Response, context: Context): Unit = {
    val responseHeaders = response.getHeaders
    context.headers.foreach { case (name, value) => responseHeaders.add(name, value) }
  }

  private def getRequestContext(request: Request): Context = {
    val requestHeaders = request.getHeaders
    val headers = requestHeaders.getFieldNamesCollection.asScala.flatMap { name =>
      requestHeaders.getValues(name).asScala.map(value => name -> value)
    }.toSeq
    request.getHttpURI.toURI
    HttpContext(
      transportContext = Some(request),
      method = Some(HttpMethod.valueOf(request.getMethod)),
      headers = headers,
      peer = Some(client(request)),
    ).url(request.getHttpURI.toURI)
  }

  private def client(request: Request): String = {
    val address = Request.getRemoteAddr(request)
    val forwardedFor = request.getHeaders.getValues(HttpHeader.X_FORWARDED_FOR.name).asScala.nextOption()
    val nodeId = request.getHeaders.getValues(headerRpcNodeId).asScala.nextOption()
    ServerHttpHandler.client(address, forwardedFor, nodeId)
  }
}

object JettyHttpEndpoint {

  /** Request context type. */
  type Context = HttpContext[Request]
}
