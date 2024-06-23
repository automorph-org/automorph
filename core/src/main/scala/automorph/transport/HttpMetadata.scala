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
 * @param id
 *   request identifier
 * @tparam Context
 *   HTTP context type
 */
private[automorph] final case class HttpMetadata[Context <: HttpContext[?]](
  context: Context,
  protocol: Protocol,
  id: String = Random.id,
) {
  lazy val contentType: String = context.contentType.getOrElse("")
  lazy val properties: Map[String, String] = ListMap(
    LogProperties.requestId -> id,
    LogProperties.protocol -> protocol.toString,
  ) ++ context.url.map(LogProperties.url -> _.toString)
    ++ context.method.map(LogProperties.method -> _.name)
    ++ context.statusCode.map(LogProperties.status -> _.toString)
    ++ context.peer.map(LogProperties.client -> _)
  lazy val statusCodeOrOk: Int = context.statusCode.getOrElse(statusOk)
}

private[automorph] object HttpMetadata {
  val headerXForwardedFor = "X-Forwarded-For"
  val statusOk = 200
}
