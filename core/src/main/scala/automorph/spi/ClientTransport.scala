package automorph.spi

/**
 * Client transport protocol plugin.
 *
 * Passively sends requests and receives responses to and from a remote endpoint using specific transport protocol.
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
   * An optional request context is used to supply additional information needed to send the request.
   *
   * @param requestBody
   *   request message body
   * @param requestContext
   *   request context
   * @param requestId
   *   request correlation identifier
   * @param mediaType
   *   message media (MIME) type.
   * @return
   *   response message and context
   */
  def call(
    requestBody: Array[Byte],
    requestContext: Context,
    requestId: String,
    mediaType: String,
  ): Effect[(Array[Byte], Context)]

  /**
   * Sends a request to a remote endpoint without waiting for a response.
   *
   * An optional request context is used to supply additional information needed to send the request.
   *
   * @param requestBody
   *   request message body
   * @param requestContext
   *   request context
   * @param requestId
   *   request correlation identifier
   * @param mediaType
   *   message media (MIME) type.
   * @return
   *   nothing
   */
  def tell(requestBody: Array[Byte], requestContext: Context, requestId: String, mediaType: String): Effect[Unit]

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
   *   active RPC server
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
