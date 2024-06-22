package automorph.transport

import automorph.log.LogProperties
import automorph.transport.HttpMetadata.statusOk
import automorph.util.Random
import scala.collection.immutable.ListMap

/**
 * HTTP or WebSocket message metadata.
 *
 * @param context
 *   HTTP context
 * @param protocol
 *   transport protocol
 * @param method
 *   HTTP method
 * @param url
 *   URL
 * @param contentType
 *   content type
 * @param statusCode
 *   HTTP status code
 * @param id
 *   request identifier
 * @tparam Context
 *   HTTP context type
 */
private[automorph] final case class HttpMetadata[Context <: HttpContext[?]](
  context: Context,
  protocol: Protocol,
  method: Option[String] = None,
  url: Option[String],
  contentType: String,
  statusCode: Option[Int],
  id: String = Random.id,
) {
  lazy val properties: Map[String, String] = ListMap(
    LogProperties.requestId -> id,
    LogProperties.protocol -> protocol.toString,
  ) ++ url.map(LogProperties.url -> _)
    ++ method.map(LogProperties.method -> _)
    ++ statusCode.map(LogProperties.status -> _.toString)
    ++ context.peer.map(LogProperties.client -> _)
  lazy val statusCodeOrOk: Int = statusCode.getOrElse(statusOk)
}

private[automorph] object HttpMetadata {
  val headerXForwardedFor = "X-Forwarded-For"
  val statusOk = 200

  def apply[Context <: HttpContext[?]](
    context: Context,
    protocol: Protocol,
    url: String,
    method: Option[String],
  ): HttpMetadata[Context] = {
    val contentType = context.contentType.getOrElse("")
    HttpMetadata(context, protocol, method, Some(url), contentType, None)
  }
}
