package automorph.spi

/**
 * Existing transport protocol server integration plugin.
 *
 * Passively parses requests to be processed by the RPC handler and creates responses for specific server.
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
