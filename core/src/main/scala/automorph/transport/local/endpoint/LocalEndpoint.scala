package automorph.transport.local.endpoint

import automorph.spi.{EffectSystem, EndpointTransport, RequestHandler}
import automorph.transport.local.LocalContext
import automorph.transport.local.endpoint.LocalEndpoint.Context

/**
 * Local endpoint transport plugin.
 *
 * Processes RPC API requests directly locally to its RPC request handler.
 *
 * @constructor
 *   Creates a local endpoint transport plugin
 * @param effectSystem
 *   effect system plugin
 * @param context
 *   default request context
 * @param handler
 *   RPC request handler
 * @tparam Effect
 *   effect type
 */
final case class LocalEndpoint[Effect[_]](
  effectSystem: EffectSystem[Effect],
  context: Context = LocalContext.defaultContext,
  handler: RequestHandler[Effect, Context] = RequestHandler.dummy[Effect, Context],
) extends EndpointTransport[Effect, Context, Unit] {

  override def adapter: Unit =
    ()

  override def withHandler(handler: RequestHandler[Effect, Context]): LocalEndpoint[Effect] =
    copy(handler = handler)
}

case object LocalEndpoint {

  /** Message context type. */
  type Context = LocalContext
}
