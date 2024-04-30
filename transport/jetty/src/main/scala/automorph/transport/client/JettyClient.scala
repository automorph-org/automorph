package automorph.transport.client

import automorph.log.{LogProperties, Logging, MessageLog}
import automorph.spi.AsyncEffectSystem.Completable
import automorph.spi.{AsyncEffectSystem, ClientTransport, EffectSystem}
import automorph.transport.HttpClientBase.{completableEffect, overrideUrl}
import automorph.transport.client.JettyClient.{Context, Transport}
import automorph.transport.{HttpContext, HttpMethod, Protocol}
import automorph.util.Extensions.{ByteArrayOps, EffectOps}
import org.eclipse.jetty.client.api.{Request, Result}
import org.eclipse.jetty.client.util.{BufferingResponseListener, BytesRequestContent}
import org.eclipse.jetty.client.{HttpClient, api}
import org.eclipse.jetty.http.HttpHeader
import org.eclipse.jetty.websocket.api.{Session, WebSocketListener, WriteCallback}
import org.eclipse.jetty.websocket.client.{ClientUpgradeRequest, WebSocketClient}
import org.eclipse.jetty.{http, websocket}
import java.net.URI
import java.util
import java.util.concurrent.TimeUnit
import scala.collection.immutable.ListMap
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
 * @param httpClient
 *   Jetty HTTP client
 * @tparam Effect
 *   effect type
 */
final case class JettyClient[Effect[_]](
  effectSystem: EffectSystem[Effect],
  url: URI,
  method: HttpMethod = HttpMethod.Post,
  httpClient: HttpClient = new HttpClient,
) extends ClientTransport[Effect, Context] with Logging {

  private type GenericRequest = Either[Request, (Effect[websocket.api.Session], Effect[Response], Array[Byte])]
  private type Response = (Array[Byte], Option[Int], Seq[(String, String)])

  private val webSocketsSchemePrefix = "ws"
  private val webSocketClient = new WebSocketClient(httpClient)
  private val log = MessageLog(logger, Protocol.Http.name)
  implicit private val system: EffectSystem[Effect] = effectSystem

  override def call(
    requestBody: Array[Byte],
    requestContext: Context,
    requestId: String,
    mediaType: String,
  ): Effect[(Array[Byte], Context)] =
    // Send the request
    createRequest(requestBody, mediaType, requestContext).flatMap { case (request, requestUrl) =>
      val protocol = request.fold(_ => Protocol.Http, _ => Protocol.WebSocket)
      send(request, requestUrl, requestId, protocol).either.flatMap { result =>
        lazy val responseProperties = ListMap(
          LogProperties.requestId -> requestId,
          LogProperties.url -> requestUrl.toString,
        )

        // Process the response
        result.fold(
          error => {
            log.failedReceiveResponse(error, responseProperties, protocol.name)
            effectSystem.failed(error)
          },
          response => {
            val (responseBody, statusCode, _) = response
            log.receivedResponse(
              responseProperties ++ statusCode.map(LogProperties.status -> _.toString),
              protocol.name,
            )
            effectSystem.successful(responseBody -> responseContext(response))
          },
        )
      }
    }

  override def tell(
    requestBody: Array[Byte],
    requestContext: Context,
    requestId: String,
    mediaType: String,
  ): Effect[Unit] =
    createRequest(requestBody, mediaType, requestContext).flatMap { case (request, requestUrl) =>
      val protocol = request.fold(_ => Protocol.Http, _ => Protocol.WebSocket)
      send(request, requestUrl, requestId, protocol).map(_ => ())
    }

  override def context: Context =
    Transport.context.url(url).method(method)

  override def init(): Effect[Unit] =
    effectSystem.evaluate(this.synchronized {
      if (!httpClient.isStarted) {
        httpClient.start()
      } else {
        throw new IllegalStateException(s"${getClass.getSimpleName} already initialized")
      }
      webSocketClient.start()
    })

  override def close(): Effect[Unit] =
    effectSystem.evaluate(this.synchronized {
      webSocketClient.stop()
      httpClient.stop()
    })

  private def send(
    request: GenericRequest,
    requestUrl: URI,
    requestId: String,
    protocol: Protocol,
  ): Effect[Response] = {
    lazy val response = request.fold(sendHttp, sendWebSocket)
    lazy val requestProperties = ListMap(
      LogProperties.requestId -> requestId,
      LogProperties.url -> requestUrl.toString,
    ) ++ request.swap.toOption.map(_.getMethod).map(LogProperties.method -> _)
    log.sendingRequest(requestProperties, protocol.name)
    response.either.flatMap(_.fold(
      error => {
        log.failedSendRequest(error, requestProperties, protocol.name)
        effectSystem.failed(error)
      },
      response => {
        log.sentRequest(requestProperties, protocol.name)
        effectSystem.successful(response)
      },
    ))
  }

  private def sendHttp(httpRequest: Request): Effect[Response] =
    effectSystem match {
      case asyncSystem: AsyncEffectSystem[?] =>
        val completableResponseEffect = asyncSystem.asInstanceOf[AsyncEffectSystem[Effect]].completable[Response]
        completableResponseEffect.flatMap { completableResponse =>
          val responseListener = new BufferingResponseListener {

            override def onComplete(result: Result): Unit =
              Option(result.getResponseFailure)
                .map(error => completableResponse.fail(error).runAsync)
                .getOrElse(completableResponse.succeed(httpResponse(result.getResponse, getContent)).runAsync)
          }
          httpRequest.send(responseListener)
          completableResponse.effect
        }
      case _ =>
        effectSystem.evaluate(httpRequest.send()).map(response => httpResponse(response, response.getContent))
    }

  private def sendWebSocket(webSocketRequest: (Effect[Session], Effect[Response], Array[Byte])): Effect[Response] = {
    val (webSocketEffect, resultEffect, requestBody) = webSocketRequest
    withCompletable { asyncSystem =>
      asyncSystem.completable[Unit].flatMap { completableRequestSent =>
        webSocketEffect.flatMap { webSocket =>
          val writeCallback = new WriteCallback {

            override def writeSuccess(): Unit =
              completableRequestSent.succeed {}.runAsync

            override def writeFailed(error: Throwable): Unit =
              completableRequestSent.fail(error).runAsync
          }
          webSocket.getRemote.sendBytes(requestBody.toByteBuffer, writeCallback)
          completableRequestSent.effect.flatMap(_ => resultEffect)
        }
      }
    }
  }

  private def httpResponse(response: api.Response, responseBody: Array[Byte]): Response = {
    val headers = response.getHeaders.asScala.map(field => field.getName -> field.getValue).toSeq
    (responseBody, Some(response.getStatus), headers)
  }

  private def withCompletable[T](function: AsyncEffectSystem[Effect] => Effect[T]): Effect[T] =
    effectSystem match {
      case asyncSystem: AsyncEffectSystem[?] => function(asyncSystem.asInstanceOf[AsyncEffectSystem[Effect]])
      case _ => effectSystem.failed(new IllegalArgumentException(
          s"""${Protocol.WebSocket} not available for effect system
            | not supporting completable effects: ${effectSystem.getClass.getName}""".stripMargin
        ))
    }

  private def createRequest(
    requestBody: Array[Byte],
    mediaType: String,
    requestContext: Context,
  ): Effect[(GenericRequest, URI)] = {
    val baseUrl = requestContext.transportContext.map(transport => transport.request.getURI).getOrElse(url)
    val requestUrl = overrideUrl(baseUrl, requestContext)
    requestUrl.getScheme.toLowerCase match {
      case scheme if scheme.startsWith(webSocketsSchemePrefix) =>
        // Create WebSocket request
        withCompletable(asyncSystem =>
          effectSystem.evaluate {
            val completableResponse = asyncSystem.completable[Response]
            val response = completableResponse.flatMap(_.effect)
            val upgradeRequest = createWebSocketRequest(requestContext, requestUrl)
            val webSocket = connectWebSocket(upgradeRequest, requestUrl, completableResponse, asyncSystem)
            Right((webSocket, response, requestBody)) -> requestUrl
          }
        )
      case _ =>
        // Create HTTP request
        effectSystem.evaluate {
          val httpRequest = createHttpRequest(requestBody, requestUrl, mediaType, requestContext)
          Left(httpRequest) -> httpRequest.getURI
        }
    }
  }

  private def createHttpRequest(
    requestBody: Array[Byte],
    requestUrl: URI,
    mediaType: String,
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
      httpFields.put(HttpHeader.CONTENT_TYPE, mediaType)
      httpFields.put(HttpHeader.ACCEPT, mediaType)
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

  private def connectWebSocket(
    upgradeRequest: ClientUpgradeRequest,
    requestUrl: URI,
    responseEffect: Effect[Completable[Effect, Response]],
    asyncSystem: AsyncEffectSystem[Effect],
  ): Effect[websocket.api.Session] =
    responseEffect.flatMap { response =>
      completableEffect(webSocketClient.connect(webSocketListener(response), requestUrl, upgradeRequest), asyncSystem)
    }

  private def webSocketListener(response: Completable[Effect, Response]): WebSocketListener =
    new WebSocketListener {

      override def onWebSocketBinary(payload: Array[Byte], offset: Int, length: Int): Unit = {
        val responseBody = util.Arrays.copyOfRange(payload, offset, offset + length)
        response.succeed((responseBody, None, Seq())).runAsync
      }

      override def onWebSocketError(error: Throwable): Unit =
        response.fail(error).runAsync
    }

  private def createWebSocketRequest(httpContext: Context, requestUrl: URI): ClientUpgradeRequest = {
    // Headers
    val transportRequest = httpContext
      .transportContext.map(_.request)
      .getOrElse(httpClient.newRequest(requestUrl))
    val transportHeaders = transportRequest.getHeaders.asScala.map(field => field.getName -> field.getValue)
    val headers = transportHeaders ++ httpContext.headers
    val request = new ClientUpgradeRequest
    headers.toSeq.groupBy(_._1).view.mapValues(_.map(_._2)).toSeq.foreach { case (name, values) =>
      request.setHeader(name, values.asJava)
    }

    // Timeout
    val timeout = httpContext.timeout.map(_.toMillis).getOrElse(transportRequest.getTimeout)
    request.setTimeout(timeout, TimeUnit.MILLISECONDS)
    request
  }

  private def responseContext(response: Response): Context = {
    val (_, statusCode, headers) = response
    statusCode.map(context.statusCode).getOrElse(context).headers(headers*)
  }
}

object JettyClient {

  /** Request context type. */
  type Context = HttpContext[Transport]

  /** Transport-specific context. */
  final case class Transport(request: Request)

  object Transport {

    /** Implicit default context value. */
    implicit val context: HttpContext[Transport] = HttpContext()
  }
}
