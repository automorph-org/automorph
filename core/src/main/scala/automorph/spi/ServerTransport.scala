package automorph.spi

/**
 * RPC server transport layer plugin.
 *
 * Enables RPC server to receive requests from remote client and send responses using specific transport protocol.
 *
 * @tparam Effect
 *   effect type
 * @tparam Context
 *   RPC message context type
 * @tparam Adapter
 *   transport layer adapter type
 */
trait ServerTransport[Effect[_], Context, Adapter] {

  /** Effect system plugin. */
  def effectSystem: EffectSystem[Effect]

  /** Adapter to be used to integrate this transport plugin into an existing server infrastructure. */
  def adapter: Adapter

  /**
   * Starts this server to process incoming requests.
   *
   * @return
   *   nothing
   */
  def init(): Effect[Unit]

  /**
   * Stops this server transport freeing the underlying resources.
   *
   * @return
   *   nothing
   */
  def close(): Effect[Unit]

  /**
   * Create a copy of this server transport with specified RPC request handler.
   *
   * @param handler
   *   RPC request handler
   * @return
   *   server transport
   */
  def requestHandler(handler: RpcHandler[Effect, Context]): ServerTransport[Effect, Context, Adapter]
}
