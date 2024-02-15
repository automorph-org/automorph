package automorph.transport.generic.endpoint

import automorph.spi.{EffectSystem, EndpointTransport, RequestHandler}

/**
 * Generic endpoint transport plugin.
 *
 * Passes RPC API requests directly to the specified RPC request handler.
 *
 * @constructor
 *   Creates a generic endpoint transport plugin
 * @param effectSystem
 *   effect system plugin
 * @param handler
 *   RPC request handler
 * @tparam Effect
 *   effect type
 * @tparam Context
 *   RPC message context type
 */
final case class GenericEndpoint[Effect[_], Context](
  effectSystem: EffectSystem[Effect],
  handler: RequestHandler[Effect, Context] = RequestHandler.dummy[Effect, Context],
) extends EndpointTransport[Effect, Context, Unit] {

  override def adapter: Unit =
    ()

  override def withHandler(handler: RequestHandler[Effect, Context]): GenericEndpoint[Effect, Context] =
    copy(handler = handler)
}

object GenericEndpoint {

  /**
   * Generic endpoint transport builder.
   *
   * @constructor
   *   Creates a new generic endpoint transport builder.
   * @tparam Context
   *   RPC message context type
   */
  final case class GenericEndpointBuilder[Context]() {

    /**
     * Creates a new generic endpoint transport plugin with specified effect system.
     *
     * @param effectSystem
     *   effecty protocol plugin
     * @tparam Effect
     *   effect type
     * @return
     *   RPC endpoint builder
     */
    def effectSystem[Effect[_]](effectSystem: EffectSystem[Effect]): GenericEndpoint[Effect, Context] =
      GenericEndpoint(effectSystem)
  }

  /**
   * Creates a new generic endpoint transport builder with specified RPC message contex type.
   *
   * @tparam Context
   *   RPC message context type
   * @return
   *   generic endpoint transport builder
   */
  def context[Context]: GenericEndpointBuilder[Context] =
    GenericEndpointBuilder()
}
