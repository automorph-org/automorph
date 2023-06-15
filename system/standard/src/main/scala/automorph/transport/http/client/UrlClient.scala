package automorph.transport.http.client

import automorph.log.{LogProperties, Logging, MessageLog}
import automorph.spi.{ClientTransport, EffectSystem}
import automorph.transport.http.client.UrlClient.{Context, TransportContext}
import automorph.transport.http.{HttpContext, HttpMethod, Protocol}
import automorph.util.Extensions.{EffectOps, TryOps, InputStreamOps}
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
  private implicit val system: EffectSystem[Effect] = effectSystem
  System.setProperty("sun.net.http.allowRestrictedHeaders", "true")

  override def call(
    requestBody: Array[Byte],
    requestContext: Context,
    requestId: String,
    mediaType: String,
  ): Effect[(Array[Byte], Context)] =
    // Send the request
    send(requestBody, requestId, mediaType, requestContext).flatMap { connection =>
      effectSystem.evaluate {
        lazy val responseProperties = ListMap(
          LogProperties.requestId -> requestId,
          "URL" -> connection.getURL.toExternalForm
        )

        // Process the response
        log.receivingResponse(responseProperties)
        connection.getResponseCode
        val responseBody = Option(connection.getErrorStream).getOrElse(connection.getInputStream).toByteArray
        log.receivedResponse(responseProperties + ("Status" -> connection.getResponseCode.toString))
        responseBody -> getResponseContext(connection)
      }
    }

  override def tell(
    requestBody: Array[Byte],
    requestContext: Context,
    requestId: String,
    mediaType: String,
  ): Effect[Unit] =
    send(requestBody, requestId, mediaType, requestContext).map(_ => ())

  override def context: Context =
    TransportContext.defaultContext.url(url).method(method)

  override def init(): Effect[Unit] =
    effectSystem.successful {}

  override def close(): Effect[Unit] =
    effectSystem.successful {}

  private def send(
    request: Array[Byte],
    requestId: String,
    mediaType: String,
    requestContext: Context,
  ): Effect[HttpURLConnection] =
    effectSystem.evaluate {
      // Create the request
      val connection = createConnection(requestContext)
      val httpMethod = setConnectionProperties(connection, request, mediaType, requestContext)

      // Log the request
      lazy val requestProperties = ListMap(
        LogProperties.requestId -> requestId,
        "URL" -> connection.getURL.toExternalForm,
        "Method" -> httpMethod
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

  private def createConnection(requestContext: Context): HttpURLConnection = {
    val requestUrl = requestContext.overrideUrl(
      requestContext.transportContext.map(_.connection.getURL.toURI).getOrElse(url)
    )
    requestUrl.toURL.openConnection().asInstanceOf[HttpURLConnection]
  }

  private def setConnectionProperties(
    connection: HttpURLConnection,
    requestBody: Array[Byte],
    mediaType: String,
    requestContext: Context,
  ): String = {
    // Method
    val transportConnection = requestContext.transportContext.map(_.connection).getOrElse(connection)
    val requestMethod =
      requestContext.method.map(_.name).orElse(requestContext.transportContext.map(_.connection.getRequestMethod))
        .getOrElse(method.name)
    require(httpMethods.contains(requestMethod), s"Invalid HTTP method: $requestMethod")
    connection.setRequestMethod(requestMethod)

    // Headers
    val transportHeaders = transportConnection.getRequestProperties.asScala.toSeq.flatMap { case (name, values) =>
      values.asScala.map(name -> _)
    }
    (transportHeaders ++ requestContext.headers).foreach { case (name, value) =>
      connection.setRequestProperty(name, value)
    }
    connection.setRequestProperty(contentLengthHeader, requestBody.length.toString)
    connection.setRequestProperty(contentTypeHeader, mediaType)
    connection.setRequestProperty(acceptHeader, mediaType)

    // Timeout & follow redirects
    connection
      .setConnectTimeout(requestContext.timeout.map(_.toMillis.toInt).getOrElse(transportConnection.getConnectTimeout))
    connection.setReadTimeout(
      requestContext.timeout.map {
        case Duration.Inf => 0
        case duration => duration.toMillis.toInt
      }.getOrElse(transportConnection.getReadTimeout)
    )
    connection
      .setInstanceFollowRedirects(
        requestContext.followRedirects.getOrElse(transportConnection.getInstanceFollowRedirects)
      )
    requestMethod
  }

  private def getResponseContext(connection: HttpURLConnection): Context =
    context.statusCode(connection.getResponseCode).headers(connection.getHeaderFields.asScala.toSeq.flatMap {
      case (name, values) => values.asScala.map(name -> _)
    }*)
}

case object UrlClient {

  /** Message context type. */
  type Context = HttpContext[TransportContext]

  /** Transport context. */
  final case class TransportContext(connection: HttpURLConnection)

  case object TransportContext {

    /** Implicit default context value. */
    implicit val defaultContext: HttpContext[TransportContext] = HttpContext()
  }
}
