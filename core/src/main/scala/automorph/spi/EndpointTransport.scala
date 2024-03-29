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
trait EndpointTransport[Effect[_], Context, Adapter] {

  /** Effect system plugin. */
  def effectSystem: EffectSystem[Effect]

  /** Transport layer adapter. */
  def adapter: Adapter

  /**
   * Create a copy of this endpoint transport with specified RPC request handler.
   *
   * @param handler
   *   RPC request handler
   * @return
   *   server transport
   */
  def withHandler(handler: RequestHandler[Effect, Context]): EndpointTransport[Effect, Context, Adapter]
}
