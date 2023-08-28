package automorph.transport.local.endpoint

import automorph.spi.{EffectSystem, EndpointTransport, RequestHandler}

/**
 * Local endpoint transport plugin.
 *
 * Passes RPC API requests directly to the specified RPC request handler.
 *
 * @constructor
 *   Creates a local endpoint transport plugin
 * @param effectSystem
 *   effect system plugin
 * @param handler
 *   RPC request handler
 * @tparam Effect
 *   effect type
 * @tparam Context
 *   RPC message context type
 */
final case class LocalEndpoint[Effect[_], Context](
  effectSystem: EffectSystem[Effect],
  handler: RequestHandler[Effect, Context] = RequestHandler.dummy[Effect, Context],
) extends EndpointTransport[Effect, Context, Unit] {

  override def adapter: Unit =
    ()

  override def withHandler(handler: RequestHandler[Effect, Context]): LocalEndpoint[Effect, Context] =
    copy(handler = handler)
}

object LocalEndpoint {

  /**
   * Local endpoint transport builder.
   *
   * @constructor
   *   Creates a new local endpoint transport builder.
   * @tparam Context
   *   RPC message context type
   */
  final case class LocalEndpointBuilder[Context]() {
    /**
     * Creates a new local endpoint transport plugin with specified effect system.
     *
     * @param effectSystem
     *   effecty protocol plugin
     * @tparam Effect
     *   effect type
     * @return
     *   RPC endpoint builder
     */
    def effectSystem[Effect[_]](effectSystem: EffectSystem[Effect]): LocalEndpoint[Effect, Context] =
      LocalEndpoint(effectSystem)
  }

  /**
   * Creates a new local endpoint transport builder with specified RPC message contex type.
   *
   * @tparam Context
   *   RPC message context type
   * @return
   *   local endpoint transport builder
   */
  def context[Context]: LocalEndpointBuilder[Context] =
    LocalEndpointBuilder()
}
