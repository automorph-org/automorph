package automorph.transport.local.client

import automorph.RpcException.InvalidResponse
import automorph.spi.{ClientTransport, EffectSystem, RequestHandler}
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
 * @param handler
 *   RPC request handler
 * @constructor
 *   Creates a local client transport plugin
 * @tparam Effect
 *   effect type
 * @tparam Context
 *   RPC message context type
 */
final case class LocalClient[Effect[_], Context](
  effectSystem: EffectSystem[Effect],
  context: Context,
  handler: RequestHandler[Effect, Context],
) extends ClientTransport[Effect, Context] {

  implicit private val system: EffectSystem[Effect] = effectSystem

  override def call(
    requestBody: Array[Byte],
    requestContext: Context,
    requestId: String,
    mediaType: String,
  ): Effect[(Array[Byte], Context)] = {
    val handlerResult = handler.processRequest(requestBody, requestContext, requestId)
    handlerResult.flatMap(_.map { result =>
      effectSystem.successful(result.responseBody -> result.context.getOrElse(context))
    }.getOrElse {
      effectSystem.failed(InvalidResponse("Missing call response", None.orNull))
    })
  }

  override def tell(
    requestBody: Array[Byte],
    requestContext: Context,
    requestId: String,
    mediaType: String,
  ): Effect[Unit] =
    handler.processRequest(requestBody, requestContext, requestId).map(_ => ())

  override def init(): Effect[Unit] =
    effectSystem.successful {}

  override def close(): Effect[Unit] =
    effectSystem.successful {}
}
