package automorph.spi

/**
 * Server transport protocol plugin.
 *
 * Actively receives requests to be processed by the RPC handler and sends responses using specific transport protocol.
 *
 * @tparam Effect
 *   effect type
 * @tparam Context
 *   RPC message context type
 */
trait ServerTransport[Effect[_], Context] {

  /** Effect system plugin. */
  def effectSystem: EffectSystem[Effect]

  /**
   * Create a copy of this server transport with specified RPC request handler.
   *
   * @param handler
   *   RPC request handler
   * @return
   *   server transport
   */
  def withHandler(handler: RequestHandler[Effect, Context]): ServerTransport[Effect, Context]

  /**
   * Starts this server to process incoming requests.
   *
   * @return
   *   active RPC server
   */
  def init(): Effect[Unit]

  /**
   * Stops this server transport freeing the underlying resources.
   *
   * @return
   *   nothing
   */
  def close(): Effect[Unit]
}
