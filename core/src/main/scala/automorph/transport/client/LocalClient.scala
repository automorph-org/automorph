package automorph.transport.client

import automorph.RpcException.InvalidResponse
import automorph.RpcServer
import automorph.spi.{ClientTransport, EffectSystem, RpcHandler}
import automorph.util.Extensions.EffectOps

/**
 * Local client transport plugin.
 *
 * Passes RPC API requests directly to the specified RPC request handler.
 *
 * @param effectSystem
 *   effect system plugin
 * @param context
 *   default request context
 * @param server
 *   RPC server
 * @param rpcHandler
 *   RPC request handler
 * @constructor
 *   Creates a local client transport plugin
 * @tparam Effect
 *   effect type
 * @tparam Context
 *   RPC message context type
 */
final case class LocalClient[Effect[_], Context] (
  effectSystem: EffectSystem[Effect],
  context: Context,
  server: RpcServer[?, ?, Effect, Context, ?],
  rpcHandler: RpcHandler[Effect, Context] = RpcHandler.dummy[Effect, Context],
) extends ClientTransport[Effect, Context] {

  implicit private val system: EffectSystem[Effect] = effectSystem

  override def call(
    body: Array[Byte],
    context: Context,
    id: String,
    mediaType: String,
  ): Effect[(Array[Byte], Context)] = {
    val handlerResult = server.processRequest(body, context, id)
    handlerResult.flatMap(_.map { result =>
      effectSystem.successful(result.responseBody -> result.context.getOrElse(context))
    }.getOrElse {
      effectSystem.failed(InvalidResponse("Missing call response", None.orNull))
    })
  }

  override def tell(
    body: Array[Byte],
    context: Context,
    id: String,
    mediaType: String,
  ): Effect[Unit] =
    server.processRequest(body, context, id).map(_ => ())

  override def init(): Effect[Unit] =
    effectSystem.successful {}

  override def close(): Effect[Unit] =
    effectSystem.successful {}

  override def rpcHandler(handler: RpcHandler[Effect, Context]): LocalClient[Effect, Context] =
    copy(rpcHandler = handler)
}
