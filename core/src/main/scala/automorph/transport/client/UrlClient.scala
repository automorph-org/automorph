package automorph.transport.client

import automorph.log.Logging
import automorph.spi.{ClientTransport, EffectSystem, RpcHandler}
import automorph.transport.HttpClientBase.{overrideUrl, webSocketSchemePrefix}
import automorph.transport.HttpContext.{headerAccept, headerContentLength, headerContentType}
import automorph.transport.client.UrlClient.{Context, Transport}
import automorph.transport.{ClientServerHttpSender, HttpContext, HttpListen, HttpMethod, Protocol}
import automorph.util.Extensions.InputStreamOps
import java.net.{HttpURLConnection, URI}
import scala.concurrent.duration.Duration
import scala.jdk.CollectionConverters.{ListHasAsScala, MapHasAsScala}
import scala.util.Using

/**
 * Standard JRE HttpURLConnection HTTP client message transport plugin.
 *
 * Uses the supplied RPC request as HTTP request body and returns HTTP response body as a result.
 *
 * @see
 *   [[https://en.wikipedia.org/wiki/HTTP Transport protocol]]
 * @see
 *   [[https://docs.oracle.com/javase/8/docs/api/java/net/HttpURLConnection.html API]]
 * @constructor
 *   Creates an HttpURLConnection HTTP client message transport plugin.
 * @param effectSystem
 *   effect system plugin
 * @param url
 *   remote API HTTP URL
 * @param method
 *   HTTP request method (default: POST)
 * @param listen
 *   listen for RPC requests from the server settings (default: disabled)
 * @param rpcNodeId
 *   RPC node identifier
 * @param rpcHandler
 *   RPC request handler
 * @tparam Effect
 *   effect type
 */
final case class UrlClient[Effect[_]](
  effectSystem: EffectSystem[Effect],
  url: URI,
  method: HttpMethod = HttpMethod.Post,
  listen: HttpListen = HttpListen(),
  rpcNodeId: Option[String] = None,
  rpcHandler: RpcHandler[Effect, Context] = RpcHandler.dummy[Effect, Context],
) extends ClientTransport[Effect, Context] with Logging {

  private type Request = (Array[Byte], HttpURLConnection)

  private val httpMethods = HttpMethod.values.map(_.name).toSet
  private val sender = ClientServerHttpSender(createRequest, sendRequest, url, method, listen, rpcNodeId, effectSystem)
  System.setProperty("sun.net.http.allowRestrictedHeaders", "true")

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
    effectSystem.evaluate(sender.init())

  override def close(): Effect[Unit] =
    effectSystem.successful {}

  override def rpcHandler(handler: RpcHandler[Effect, Context]): UrlClient[Effect] =
    copy(rpcHandler = handler)

  private def createRequest(
    requestBody: Array[Byte],
    context: Context,
    contentType: String,
  ): (Request, Context, Protocol) = {
    val baseUrl = context.transportContext.map(_.connection.getURL.toURI).getOrElse(url)
    val requestUrl = overrideUrl(baseUrl, context)
    requestUrl.getScheme.toLowerCase match {
      case scheme if scheme.startsWith(webSocketSchemePrefix) =>
        throw new UnsupportedOperationException(s"WebSocket URLs not supported: $requestUrl")
      case _ =>
        // Method
        val connection = requestUrl.toURL.openConnection().asInstanceOf[HttpURLConnection]
        val transportConnection = context.transportContext.map(_.connection).getOrElse(connection)
        val requestMethod = context.method.map(_.name)
          .orElse(context.transportContext.map(_.connection.getRequestMethod))
          .getOrElse(method.name)
        require(httpMethods.contains(requestMethod), s"Invalid HTTP method: $requestMethod")
        connection.setRequestMethod(requestMethod)

        // Headers
        val transportHeaders = transportConnection.getRequestProperties.asScala.toSeq.flatMap { case (name, values) =>
          values.asScala.map(name -> _)
        }
        val headers = transportHeaders ++ context.headers
        headers.foreach { case (name, value) => connection.setRequestProperty(name, value) }
        connection.setRequestProperty(headerContentLength, requestBody.length.toString)
        connection.setRequestProperty(headerContentType, contentType)
        connection.setRequestProperty(headerAccept, contentType)

        // Timeouts & follow redirects
        connection.setConnectTimeout(
          context.timeout.map(_.toMillis.toInt).getOrElse(transportConnection.getConnectTimeout)
        )
        connection.setReadTimeout(context.timeout.map {
          case Duration.Inf => 0
          case duration => duration.toMillis.toInt
        }.getOrElse(transportConnection.getReadTimeout))
        connection.setInstanceFollowRedirects(
          context.followRedirects.getOrElse(transportConnection.getInstanceFollowRedirects)
        )
        val requestContext =
          context.url(connection.getURL.toURI).method(HttpMethod.valueOf(connection.getRequestMethod))
        (requestBody -> connection, requestContext, Protocol.Http)
    }
  }

  private def sendRequest(request: Request, requestContext: Context): Effect[(Array[Byte], Context)] = {
    val (requestBody, connection) = request
    effectSystem.evaluate {
      connection.setDoOutput(true)
      Using(connection.getOutputStream) { stream =>
        stream.write(requestBody)
        stream.flush()
      }.get
      val responseCode = connection.getResponseCode
      val responseBody = Option(connection.getErrorStream).getOrElse(connection.getInputStream).toByteArray
      val responseContext = requestContext
        .statusCode(responseCode)
        .headers(connection.getHeaderFields.asScala.toSeq.flatMap {
          case (name, values) => values.asScala.map(name -> _)
        }*)
      responseBody -> responseContext
    }
  }
}

object UrlClient {

  /** Message context type. */
  type Context = HttpContext[Transport]

  /** Transport-specific context. */
  final case class Transport(connection: HttpURLConnection)

  object Transport {

    /** Implicit default context value. */
    implicit val context: HttpContext[Transport] = HttpContext()
  }
}
