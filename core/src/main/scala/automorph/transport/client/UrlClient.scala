package automorph.transport.client

import automorph.log.{LogProperties, Logging, MessageLog}
import automorph.spi.{ClientTransport, EffectSystem}
import automorph.transport.HttpClientBase.overrideUrl
import automorph.transport.client.UrlClient.{Context, Transport}
import automorph.transport.{HttpContext, HttpMethod, Protocol}
import automorph.util.Extensions.{EffectOps, InputStreamOps, TryOps}
import java.net.{HttpURLConnection, URI}
import scala.collection.immutable.ListMap
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
 * @tparam Effect
 *   effect type
 */
final case class UrlClient[Effect[_]](
  effectSystem: EffectSystem[Effect],
  url: URI,
  method: HttpMethod = HttpMethod.Post,
) extends ClientTransport[Effect, Context] with Logging {

  private val contentLengthHeader = "Content-Length"
  private val contentTypeHeader = "Content-Type"
  private val acceptHeader = "Accept"
  private val httpMethods = HttpMethod.values.map(_.name).toSet
  private val log = MessageLog(logger, Protocol.Http.name)
  implicit private val system: EffectSystem[Effect] = effectSystem
  System.setProperty("sun.net.http.allowRestrictedHeaders", "true")

  override def call(
    body: Array[Byte],
    context: Context,
    id: String,
    mediaType: String,
  ): Effect[(Array[Byte], Context)] =
    // Send the request
    send(body, id, mediaType, context).flatMap { connection =>
      effectSystem.evaluate {
        lazy val responseProperties = ListMap(
          LogProperties.requestId -> id,
          LogProperties.url -> connection.getURL.toExternalForm,
        )

        // Process the response
        log.receivingResponse(responseProperties)
        connection.getResponseCode
        val responseBody = Option(connection.getErrorStream).getOrElse(connection.getInputStream).toByteArray
        log.receivedResponse(responseProperties + (LogProperties.status -> connection.getResponseCode.toString))
        responseBody -> responseContext(connection)
      }
    }

  override def tell(
    body: Array[Byte],
    context: Context,
    id: String,
    mediaType: String,
  ): Effect[Unit] =
    send(body, id, mediaType, context).map(_ => ())

  override def context: Context =
    Transport.context.url(url).method(method)

  override def init(): Effect[Unit] =
    effectSystem.successful {}

  override def close(): Effect[Unit] =
    effectSystem.successful {}

  private def send(
    request: Array[Byte],
    requestId: String,
    mediaType: String,
    context: Context,
  ): Effect[HttpURLConnection] =
    effectSystem.evaluate {
      // Create the request
      val connection = openConnection(context)
      val httpMethod = setConnectionProperties(connection, request, mediaType, context)

      // Log the request
      lazy val requestProperties = ListMap(
        LogProperties.requestId -> requestId,
        LogProperties.url -> connection.getURL.toExternalForm,
        LogProperties.method -> httpMethod,
      )
      log.sendingRequest(requestProperties)

      // Send the request
      connection.setDoOutput(true)
      val outputStream = connection.getOutputStream
      val write = Using(outputStream) { stream =>
        stream.write(request)
        stream.flush()
      }
      write.onError(error => log.failedSendRequest(error, requestProperties)).get
      log.sentRequest(requestProperties)
      connection
    }

  private def openConnection(context: Context): HttpURLConnection = {
    val baseUrl = context.transportContext.map(_.connection.getURL.toURI).getOrElse(url)
    val requestUrl = overrideUrl(baseUrl, context)
    requestUrl.toURL.openConnection().asInstanceOf[HttpURLConnection]
  }

  private def setConnectionProperties(
    connection: HttpURLConnection,
    requestBody: Array[Byte],
    mediaType: String,
    context: Context,
  ): String = {
    // Method
    val transportConnection = context.transportContext.map(_.connection).getOrElse(connection)
    val contextMethod = context.method.map(_.name)
    val requestMethod = contextMethod
      .orElse(context.transportContext.map(_.connection.getRequestMethod))
      .getOrElse(method.name)
    require(httpMethods.contains(requestMethod), s"Invalid HTTP method: $requestMethod")
    connection.setRequestMethod(requestMethod)

    // Headers
    val transportHeaders = transportConnection
      .getRequestProperties.asScala.toSeq
      .flatMap { case (name, values) =>
        values.asScala.map(name -> _)
      }
    val headers = transportHeaders ++ context.headers
    headers.foreach { case (name, value) => connection.setRequestProperty(name, value) }
    connection.setRequestProperty(contentLengthHeader, requestBody.length.toString)
    connection.setRequestProperty(contentTypeHeader, mediaType)
    connection.setRequestProperty(acceptHeader, mediaType)

    // Timeout & follow redirects
    connection.setConnectTimeout(
      context.timeout.map(_.toMillis.toInt).getOrElse(transportConnection.getConnectTimeout)
    )
    connection.setReadTimeout(
      context.timeout.map {
        case Duration.Inf => 0
        case duration => duration.toMillis.toInt
      }.getOrElse(transportConnection.getReadTimeout)
    )
    connection.setInstanceFollowRedirects(
      context.followRedirects.getOrElse(transportConnection.getInstanceFollowRedirects)
    )
    requestMethod
  }

  private def responseContext(connection: HttpURLConnection): Context =
    context
      .statusCode(connection.getResponseCode)
      .headers(connection.getHeaderFields.asScala.toSeq.flatMap {
        case (name, values) => values.asScala.map(name -> _)
      }*)
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
