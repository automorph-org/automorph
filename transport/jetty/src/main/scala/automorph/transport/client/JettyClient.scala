package automorph.transport.client

import automorph.log.{Logger, Logging, MessageLog}
import automorph.spi.EffectSystem.Completable
import automorph.spi.{ClientTransport, EffectSystem, RpcHandler}
import automorph.transport.HttpClientBase.{
  completableEffect, overrideUrl, webSocketCloseReason, webSocketCloseStatusCode, webSocketConnectionClosed,
  webSocketFailedClose, webSocketSchemePrefix, webSocketUnexpectedMessage,
}
import automorph.transport.HttpContext.headerRpcListen
import automorph.transport.ServerHttpHandler.valueRpcListen
import automorph.transport.client.JettyClient.{
  ClosedCallback, Context, FrameListener, ResponseListener, SentCallback, Transport,
}
import automorph.transport.{ClientServerHttpSender, ConnectionPool, HttpContext, HttpListen, HttpMethod, Protocol}
import automorph.util.Extensions.{ByteArrayOps, ByteBufferOps, EffectOps, StringOps}
import org.eclipse.jetty.client.{BufferingResponseListener, BytesRequestContent, HttpClient, Request, Response, Result}
import org.eclipse.jetty.http
import org.eclipse.jetty.http.HttpHeader
import org.eclipse.jetty.websocket.api.annotations.{OnWebSocketClose, OnWebSocketError, OnWebSocketMessage, WebSocket}
import org.eclipse.jetty.websocket.api.{Callback, Session}
import org.eclipse.jetty.websocket.client.{ClientUpgradeRequest, WebSocketClient}
import java.net.URI
import java.nio.ByteBuffer
import java.util.concurrent.TimeUnit
import scala.annotation.unused
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
 *   listen for RPC requests from the server settings (default: disabled)
 * @param httpClient
 *   Jetty HTTP client
 * @param rpcNodeId
 *   RPC node identifier
 * @param rpcHandler
 *   RPC request handler
 * @tparam Effect
 *   effect type
 */
final case class JettyClient[Effect[_]](
  effectSystem: EffectSystem[Effect],
  url: URI,
  method: HttpMethod = HttpMethod.Post,
  listen: HttpListen = HttpListen(),
  httpClient: HttpClient = new HttpClient,
  rpcNodeId: Option[String] = None,
  rpcHandler: RpcHandler[Effect, Context] = RpcHandler.dummy[Effect, Context],
) extends ClientTransport[Effect, Context] with Logging {

  private type GenericRequest = Either[Request, (Array[Byte], ClientUpgradeRequest, URI)]

  private val webSocketClient = new WebSocketClient(httpClient)
  private val callWebSocketConnections = {
    val maxPeerConnections = Some(httpClient.getMaxConnectionsPerDestination)
    ConnectionPool(Some(openWebSocket), closeWebSocket, Protocol.WebSocket, effectSystem, maxPeerConnections)
  }
  private val listenWebSocketConnections =
    ConnectionPool(Some(openWebSocket), closeWebSocket, Protocol.WebSocket, effectSystem, Some(listen.connections))
  private val sender = ClientServerHttpSender(createRequest, sendRequest, url, method, listen, rpcNodeId, effectSystem)
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
    }
      .flatMap(_ => callWebSocketConnections.init())
      .flatMap(_ => listenWebSocketConnections.init())
      .map(_ => sender.init())

  override def close(): Effect[Unit] =
    system.evaluate(sender.close())
      .flatMap(_ => listenWebSocketConnections.close())
      .flatMap(_ => callWebSocketConnections.close())
      .map { _ =>
        this.synchronized {
          if (httpClient.isStarted) {
            webSocketClient.stop()
            httpClient.stop()
          } else {
            throw new IllegalStateException(s"${getClass.getSimpleName} already closed")
          }
        }
      }

  override def rpcHandler(handler: RpcHandler[Effect, Context]): JettyClient[Effect] =
    copy(rpcHandler = handler)

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
        // Send HTTP request
        effectSystem.completable[(Array[Byte], Response)].flatMap { expectedResponse =>
          val responseListener = ResponseListener(expectedResponse, effectSystem)
          httpRequest.send(responseListener)
          expectedResponse.effect.map { case (body, response) =>
            val headers = response.getHeaders.asScala.map(field => field.getName -> field.getValue).toSeq
            body -> requestContext.statusCode(response.getStatus).headers(headers*)
          }
        },
      { case (requestBody, clientUpgradeRequest, requestUrl) =>
        // Send WebSocket request
        effectSystem.completable[Array[Byte]].flatMap { expectedResponse =>
          val listen = requestContext.header(headerRpcListen).contains(valueRpcListen)
          val connections = if (listen) listenWebSocketConnections else callWebSocketConnections
          connections.using(
            requestUrl.toString,
            (clientUpgradeRequest, requestUrl),
            { case (session, frameListener) =>
              frameListener.expectedResponse = Some(expectedResponse)
              (if (listen) {
                 expectedResponse.effect
               } else {
                 effectSystem.completable[Unit].flatMap { expectedRequestSent =>
                   val sentCallback = SentCallback(expectedRequestSent, effectSystem)
                   session.sendBinary(requestBody.toByteBuffer, sentCallback)
                   expectedRequestSent.effect.flatMap(_ => expectedResponse.effect)
                 }
               }).map(_ -> requestContext.headers(Seq.empty[(String, String)]*))
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
    val headers = transportHeaders ++ httpContext.headers ++ Seq(HttpHeader.CONTENT_TYPE.asString -> contentType)
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
    val removeConnection = () => callWebSocketConnections.remove(requestUrl.toString, connectionId)
//    val frameListener = new WebSocketListener(FrameListener(requestUrl, removeConnection, effectSystem, logger))
    val frameListener = FrameListener(requestUrl, removeConnection, effectSystem, logger)
    completableEffect(webSocketClient.connect(frameListener, requestUrl, clientUpgradeRequest), effectSystem)
      .map(_ -> frameListener)
  }

  private def closeWebSocket(connection: (Session, FrameListener[Effect])): Effect[Unit] =
    effectSystem.evaluate {
      val (session, _) = connection
      session.close(
        webSocketCloseStatusCode,
        webSocketCloseReason,
        ClosedCallback(session.getUpgradeRequest.getRequestURI, logger),
      )
    }
}

object JettyClient {

  /** Request context type. */
  type Context = HttpContext[Transport]

  /** Transport-specific context. */
  final case class Transport(request: Request)

  @WebSocket(autoDemand = true)
  final case class FrameListener[Effect[_]](
    url: URI,
    removeConnection: () => Unit,
    effectSystem: EffectSystem[Effect],
    logger: Logger,
    var expectedResponse: Option[Completable[Effect, Array[Byte]]] = None,
  ) {
    implicit val system: EffectSystem[Effect] = effectSystem

    @OnWebSocketMessage
    def onWebSocketBinary(payload: ByteBuffer, callback: Callback): Unit = {
      expectedResponse.map { response =>
        expectedResponse = None
        response.succeed(payload.toByteArray).runAsync
      }.getOrElse(logger.error(webSocketUnexpectedMessage, Map(MessageLog.url -> url)))
      callback.succeed()
    }

    @OnWebSocketMessage
    def onWebSocketText(message: String): Unit =
      expectedResponse.map { response =>
        expectedResponse = None
        response.succeed(message.toByteArray).runAsync
      }.getOrElse(logger.error(webSocketUnexpectedMessage, Map(MessageLog.url -> url)))

    @OnWebSocketError
    def onWebSocketError(error: Throwable): Unit = {
      removeConnection()
      expectedResponse.map { response =>
        expectedResponse = None
        response.fail(error).runAsync
      }.getOrElse(logger.error(webSocketUnexpectedMessage, Map(MessageLog.url -> url)))
    }

    @OnWebSocketClose
    def onWebSocketClose(@unused statusCode: Int, reason: String): Unit = {
      removeConnection()
      expectedResponse.foreach { response =>
        expectedResponse = None
        response.fail(new IllegalStateException(s"$webSocketConnectionClosed: $reason")).runAsync
      }
    }
  }

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
  ) extends Callback {
    implicit private val system: EffectSystem[Effect] = effectSystem

    override def succeed(): Unit =
      expectedRequestSent.succeed {}.runAsync

    override def fail(error: Throwable): Unit =
      expectedRequestSent.fail(error).runAsync
  }

  final private case class ClosedCallback(url: URI, logger: Logger) extends Callback {

    override def succeed(): Unit =
      logger.debug(webSocketConnectionClosed, Map(MessageLog.url -> url))

    override def fail(error: Throwable): Unit =
      logger.error(webSocketFailedClose, Map(MessageLog.url -> url))
  }

  object Transport {

    /** Implicit default context value. */
    implicit val context: HttpContext[Transport] = HttpContext()
  }
}
