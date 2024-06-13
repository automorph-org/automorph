package automorph.spi

/**
 * Client transport protocol plugin.
 *
 * Enables RPC client to send requests and receive responses using specific transport protocol.
 *
 * Passively sends requests and receives responses to and from a remote endpoint.
 *
 * @tparam Effect
 *   effect type
 * @tparam Context
 *   RPC message context type
 */
trait ClientTransport[Effect[_], Context] {

  /** Effect system plugin. */
  def effectSystem: EffectSystem[Effect]

  /**
   * Sends a request to a remote endpoint and retrieves the response.
   *
   * Request context is used to supply additional information needed to send the desired request.
   *
   * @param body
   *   request body
   * @param context
   *   request context
   * @param id
   *   request correlation identifier
   * @param mediaType
   *   message media (MIME) type.
   * @return
   *   response body and context
   */
  def call(body: Array[Byte], context: Context, id: String, mediaType: String): Effect[(Array[Byte], Context)]

  /**
   * Sends a request to a remote endpoint without waiting for a response.
   *
   * Request context is used to supply additional information needed to send the desired request.
   *
   * @param body
   *   request body
   * @param context
   *   request context
   * @param id
   *   request correlation identifier
   * @param mediaType
   *   message media (MIME) type.
   * @return
   *   nothing
   */
  def tell(body: Array[Byte], context: Context, id: String, mediaType: String): Effect[Unit]

  /**
   * Creates default request context based on the configuration of this client transport.
   *
   * @return
   *   request context based on the configuration of this client transport
   */
  def context: Context

  /**
   * Initializes this client to invoke remote APIs.
   *
   * @return
   *   nothing
   */
  def init(): Effect[Unit]

  /**
   * Closes this client transport freeing the underlying resources.
   *
   * @return
   *   nothing
   */
  def close(): Effect[Unit]
}
