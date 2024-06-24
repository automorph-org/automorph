package automorph.transport

import java.util.concurrent.TimeUnit
import scala.concurrent.duration.FiniteDuration

/**
 * HTTP/WebSocket client settings for listening for RPC requests from the server.
 *
 * @param connections
 *   number of opened connections reserved for listening for requests from the server
 * @param retryInterval
 *   time interval between retries of failed listen connection attempts
 */
final case class HttpListen(connections: Int = 0, retryInterval: FiniteDuration = FiniteDuration(5, TimeUnit.SECONDS))
