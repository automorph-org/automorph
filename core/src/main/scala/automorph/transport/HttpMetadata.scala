package automorph.transport

import automorph.log.MessageLog
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
    MessageLog.requestId -> id,
    MessageLog.protocol -> protocol.toString,
  ) ++ context.url.map(MessageLog.url -> _.toString)
    ++ context.method.map(MessageLog.method -> _.name)
    ++ context.statusCode.map(MessageLog.status -> _.toString)
    ++ context.peer.map(MessageLog.client -> _)
  lazy val statusCodeOrOk: Int = context.statusCode.getOrElse(statusOk)
}

private[automorph] object HttpMetadata {
  val headerXForwardedFor = "X-Forwarded-For"
  val statusOk = 200
}
