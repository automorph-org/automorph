package automorph.transport.client

import automorph.log.{LogProperties, Logger, Logging}
import automorph.spi.EffectSystem.Completable
import automorph.spi.{ClientTransport, EffectSystem}
import automorph.transport.HttpClientBase.{
  completableEffect, overrideUrl, webSocketCloseReason, webSocketCloseStatusCode, webSocketConnectionClosed,
  webSocketSchemePrefix, webSocketUnexpectedMessage,
}
import automorph.transport.client.JettyClient.{Context, FrameListener, ResponseListener, SentCallback, Transport}
import automorph.transport.{ClientServerHttpSender, ConnectionPool, HttpContext, HttpListen, HttpMethod, Protocol}
import automorph.util.Extensions.{ByteArrayOps, EffectOps}
import org.eclipse.jetty.client.HttpClient
import org.eclipse.jetty.client.api.{Request, Response, Result}
import org.eclipse.jetty.client.util.{BufferingResponseListener, BytesRequestContent}
import org.eclipse.jetty.http
import org.eclipse.jetty.http.HttpHeader
import org.eclipse.jetty.websocket.api.{Session, WebSocketListener, WriteCallback}
import org.eclipse.jetty.websocket.client.{ClientUpgradeRequest, WebSocketClient}
import java.net.URI
import java.util
import java.util.concurrent.TimeUnit
import scala.jdk.CollectionConverters.{IterableHasAsScala, SeqHasAsJava}

/**
 * Jetty HTTP & WebSocket client message transport plugin.
 *
 * The client uses the supplied RPC request as HTTP request body and returns HTTP response body as a result.
 *
 * @see
 *   [[https://en.wikipedia.org/wiki/HTTP Transport protocol]]
 * @see
 *   [[https://en.wikipedia.org/wiki/WebSocket Alternative transport protocol]]
 * @see
 *   [[https://jetty.io Library documentation]]
 * @see
 *   [[https://www.javadoc.io/doc/io.jetty/jetty-core/latest/index.html API]]
 * @constructor
 *   Creates an Jetty HTTP & WebSocket message client transport plugin.
 * @param effectSystem
 *   effect system plugin
 * @param url
 *   remote API HTTP or WebSocket URL
 * @param method
 *   HTTP request method (default: POST)
 * @param listen
 *   listen for server requests settings
 * @param httpClient
 *   Jetty HTTP client
 * @tparam Effect
 *   effect type
 */
final case class JettyClient[Effect[_]](
  effectSystem: EffectSystem[Effect],
  url: URI,
  method: HttpMethod = HttpMethod.Post,
  listen: HttpListen = HttpListen(),
  httpClient: HttpClient = new HttpClient,
) extends ClientTransport[Effect, Context] with Logging {

  private type GenericRequest = Either[Request, (Array[Byte], ClientUpgradeRequest, URI)]

  private val webSocketClient = new WebSocketClient(httpClient)
  private val webSocketConnectionPool = {
    val maxPeerConnections = Some(httpClient.getMaxConnectionsPerDestination)
    ConnectionPool(Some(openWebSocket), closeWebSocket, Protocol.WebSocket, effectSystem, maxPeerConnections)
  }
  private val sender = ClientServerHttpSender(createRequest, sendRequest, url, method, listen, effectSystem)
  implicit private val system: EffectSystem[Effect] = effectSystem

  override def call(
    body: Array[Byte],
    context: Context,
    id: String,
    mediaType: String,
  ): Effect[(Array[Byte], Context)] =
    sender.call(body, context, id, mediaType)

  override def tell(
    body: Array[Byte],
    context: Context,
    id: String,
    mediaType: String,
  ): Effect[Unit] =
    sender.tell(body, context, id, mediaType)

  override def context: Context =
    Transport.context.url(url).method(method)

  override def init(): Effect[Unit] =
    system.evaluate {
      this.synchronized {
        if (!httpClient.isStarted) {
          httpClient.start()
        } else {
          throw new IllegalStateException(s"${getClass.getSimpleName} already initialized")
        }
        webSocketClient.start()
      }
    }.flatMap(_ => webSocketConnectionPool.init()).map(_ => sender.listen())

  override def close(): Effect[Unit] =
    webSocketConnectionPool.close().map { _ =>
      this.synchronized {
        webSocketClient.stop()
        httpClient.stop()
      }
    }

  private def createRequest(
    requestBody: Array[Byte],
    context: Context,
    contentType: String,
  ): (GenericRequest, Context, Protocol) = {
    val baseUrl = context.transportContext.map(_.request.getURI).getOrElse(url)
    val requestUrl = overrideUrl(baseUrl, context)
    requestUrl.getScheme.toLowerCase match {
      case scheme if scheme.startsWith(webSocketSchemePrefix) =>
        val upgradeRequest = createWebSocketRequest(context, requestUrl, contentType)
        (Right((requestBody, upgradeRequest, requestUrl)), context.url(requestUrl), Protocol.WebSocket)
      case _ =>
        val httpRequest = createHttpRequest(requestBody, requestUrl, contentType, context)
        val requestContext = context.url(requestUrl).method(HttpMethod.valueOf(httpRequest.getMethod))
        (Left(httpRequest), requestContext, Protocol.Http)
    }
  }

  private def sendRequest(request: GenericRequest, requestContext: Context): Effect[(Array[Byte], Context)] =
    request.fold(
      httpRequest =>
        effectSystem.completable[(Array[Byte], Response)].flatMap { expectedResponse =>
          val responseListener = ResponseListener(expectedResponse, effectSystem)
          httpRequest.send(responseListener)
          expectedResponse.effect.map { case (body, response) =>
            val headers = response.getHeaders.asScala.map(field => field.getName -> field.getValue).toSeq
            body -> requestContext.statusCode(response.getStatus).headers(headers*)
          }
        },
      { case (requestBody, clientUpgradeRequest, requestUrl) =>
        effectSystem.completable[Array[Byte]].flatMap { expectedResponse =>
          webSocketConnectionPool.using(
            requestUrl.toString,
            (clientUpgradeRequest, requestUrl),
            { case (session, frameListener) =>
              frameListener.expectedResponse = Some(expectedResponse)
              effectSystem.completable[Unit].flatMap { expectedRequestSent =>
                session.getRemote.sendBytes(requestBody.toByteBuffer, SentCallback(expectedRequestSent, effectSystem))
                expectedRequestSent.effect.flatMap(_ =>
                  expectedResponse.effect.map(_ -> requestContext.headers(Seq.empty[(String, String)]*))
                )
              }
            },
          )
        }
      },
    )

  private def createHttpRequest(
    requestBody: Array[Byte],
    requestUrl: URI,
    contentType: String,
    httpContext: Context,
  ): Request = {
    // URL, method & body
    val requestMethod = http.HttpMethod.valueOf(
      httpContext.method.orElse(
        httpContext.transportContext.map(_.request.getMethod).map(HttpMethod.valueOf)
      ).getOrElse(method).name
    )
    val transportRequest = httpContext.transportContext.map(_.request).getOrElse(httpClient.newRequest(requestUrl))
    val bodyRequest = transportRequest.method(requestMethod).body(new BytesRequestContent(requestBody))

    // Headers
    val headersRequest = bodyRequest.headers { httpFields =>
      httpContext.headers.foreach { case (name, value) => httpFields.add(name, value) }
      httpFields.put(HttpHeader.CONTENT_TYPE, contentType)
      httpFields.put(HttpHeader.ACCEPT, contentType)
      ()
    }

    // Timeout & follow redirects
    val timeoutRequest = httpContext.timeout
      .map(timeout => headersRequest.timeout(timeout.toMillis, TimeUnit.MILLISECONDS))
      .getOrElse(headersRequest)
    httpContext.followRedirects
      .map(followRedirects => timeoutRequest.followRedirects(followRedirects))
      .getOrElse(timeoutRequest)
  }

  private def createWebSocketRequest(
    httpContext: Context,
    requestUrl: URI,
    contentType: String,
  ): ClientUpgradeRequest = {
    // Headers
    val transportRequest = httpContext
      .transportContext.map(_.request)
      .getOrElse(httpClient.newRequest(requestUrl))
    val transportHeaders = transportRequest.getHeaders.asScala.map(field => field.getName -> field.getValue)
    val headers = transportHeaders ++ httpContext.headers
      ++ Seq(HttpHeader.CONTENT_TYPE.asString -> contentType, HttpHeader.CONTENT_TYPE.asString -> contentType)
    val request = new ClientUpgradeRequest
    headers.toSeq.groupBy(_._1).view.mapValues(_.map(_._2)).toSeq.foreach { case (name, values) =>
      request.setHeader(name, values.asJava)
    }

    // Timeout
    val timeout = httpContext.timeout.map(_.toMillis).getOrElse(transportRequest.getTimeout)
    request.setTimeout(timeout, TimeUnit.MILLISECONDS)
    request
  }

  private def openWebSocket(
    endpoint: (ClientUpgradeRequest, URI),
    connectionId: Int,
  ): Effect[(Session, FrameListener[Effect])] = {
    val (clientUpgradeRequest, requestUrl) = endpoint
    val removeConnection = () => webSocketConnectionPool.remove(requestUrl.toString, connectionId)
    val frameListener = FrameListener(requestUrl, removeConnection, effectSystem, logger)
    completableEffect(webSocketClient.connect(frameListener, requestUrl, clientUpgradeRequest), effectSystem)
      .map(_ -> frameListener)
  }

  private def closeWebSocket(connection: (Session, FrameListener[Effect])): Effect[Unit] =
    effectSystem.evaluate {
      connection._1.close(webSocketCloseStatusCode, webSocketCloseReason)
    }
}

object JettyClient {

  /** Request context type. */
  type Context = HttpContext[Transport]

  /** Transport-specific context. */
  final case class Transport(request: Request)

  final private case class ResponseListener[Effect[_]](
    expectedResponse: Completable[Effect, (Array[Byte], Response)],
    effectSystem: EffectSystem[Effect],
  ) extends BufferingResponseListener {
    implicit private val system: EffectSystem[Effect] = effectSystem

    override def onComplete(result: Result): Unit =
      Option(result.getResponseFailure)
        .map(error => expectedResponse.fail(error).runAsync)
        .getOrElse(expectedResponse.succeed(getContent -> result.getResponse).runAsync)
  }

  final private case class SentCallback[Effect[_]](
    expectedRequestSent: Completable[Effect, Unit],
    effectSystem: EffectSystem[Effect],
  ) extends WriteCallback {
    implicit private val system: EffectSystem[Effect] = effectSystem

    override def writeSuccess(): Unit =
      expectedRequestSent.succeed {}.runAsync

    override def writeFailed(error: Throwable): Unit =
      expectedRequestSent.fail(error).runAsync
  }

  final private case class FrameListener[Effect[_]](
    url: URI,
    removeConnection: () => Unit,
    effectSystem: EffectSystem[Effect],
    logger: Logger,
    var expectedResponse: Option[Completable[Effect, Array[Byte]]] = None,
  ) extends WebSocketListener {
    implicit val system: EffectSystem[Effect] = effectSystem

    override def onWebSocketBinary(payload: Array[Byte], offset: Int, length: Int): Unit = {
      val responseBody = util.Arrays.copyOfRange(payload, offset, offset + length)
      expectedResponse.map { response =>
        expectedResponse = None
        response.succeed(responseBody).runAsync
      }.getOrElse(logger.error(webSocketUnexpectedMessage, Map(LogProperties.url -> url)))
    }

    override def onWebSocketError(error: Throwable): Unit = {
      removeConnection()
      expectedResponse.map { response =>
        expectedResponse = None
        response.fail(error).runAsync
      }.getOrElse(logger.error(webSocketUnexpectedMessage, Map(LogProperties.url -> url)))
    }

    override def onWebSocketClose(statusCode: Int, reason: String): Unit = {
      removeConnection()
      expectedResponse.foreach { response =>
        expectedResponse = None
        response.fail(new IllegalStateException(webSocketConnectionClosed)).runAsync
      }
    }
  }

  object Transport {

    /** Implicit default context value. */
    implicit val context: HttpContext[Transport] = HttpContext()
  }
}
