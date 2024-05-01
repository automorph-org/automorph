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
 * @tparam Endpoint
 *   transport layer endpoint type
 */
trait ServerTransport[Effect[_], Context, Endpoint] {

  /** Effect system plugin. */
  def effectSystem: EffectSystem[Effect]

  /** Transport layer integration endpoint. */
  def adapter: Endpoint

  /**
   * Starts this server to process incoming requests.
   *
   * @return
   *   active server transport
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
  def requestHandler(handler: RequestHandler[Effect, Context]): ServerTransport[Effect, Context, Endpoint]
}
