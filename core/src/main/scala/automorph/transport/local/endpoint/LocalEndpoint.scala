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
 * @param defaultContext
 *   default request context
 * @param handler
 *   RPC request handler
 * @tparam Effect
 *   effect type
 * @tparam Context
 *   RPC message context type
 */
final case class LocalEndpoint[Effect[_], Context](
  effectSystem: EffectSystem[Effect],
  defaultContext: Context,
  handler: RequestHandler[Effect, Context] = RequestHandler.dummy[Effect, Context],
) extends EndpointTransport[Effect, Context, Unit] {

  override def adapter: Unit =
    ()

  override def withHandler(handler: RequestHandler[Effect, Context]): LocalEndpoint[Effect, Context] =
    copy(handler = handler)
}
