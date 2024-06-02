package automorph.spi

/**
 * Existing server transport layer integration plugin.
 *
 * Enables RPC endpoint to integrate with and handle requests from an existing server infrastructure.
 *
 * Passively parses requests to be processed by the RPC handler and creates responses usable by specific server.
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

  /** Transport layer integration adapter. */
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
   * Create a copy of this endpoint transport with specified RPC request handler.
   *
   * @param handler
   *   RPC request handler
   * @return
   *   server transport
   */
  def requestHandler(handler: RequestHandler[Effect, Context]): ServerTransport[Effect, Context, Adapter]
}
