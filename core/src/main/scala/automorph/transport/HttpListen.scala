package automorph.transport

import java.util.concurrent.TimeUnit
import scala.concurrent.duration.FiniteDuration

/**
 * HTTP/WebSocket client settings for listening for RPC requests from the server.
 *
 * @param connections
 *   number of opened connections reserved for listening for requests from the server (default: 0)
 * @param retryInterval
 *   time interval between retries of failed listen connection attempts (default: 5 seconds)
 */
final case class HttpListen(connections: Int = 0, retryInterval: FiniteDuration = FiniteDuration(5, TimeUnit.SECONDS)) {

  /**
   * Set number of opened connections reserved for listening for requests from the server.
   *
   * @param connections
   *   number of connections
   * @return
   *   HTTP listen settings
   */
  def connections(connections: Int): HttpListen =
    copy(connections = connections)

  /**
   * Set time interval between retries of failed listen connection attempts.
   *
   * @param retryInterval
   *   retry time interval
   * @return
   *   HTTP listen settings
   */
  def retryInterval(retryInterval: Int): HttpListen =
    copy(connections = retryInterval)
}
