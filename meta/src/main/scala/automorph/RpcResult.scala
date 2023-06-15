package automorph

/**
 * RPC function return type containing both the actual result and RPC response context.
 *
 * @param result
 *   RPC function result
 * @param context
 *   RPC response context
 * @tparam Result
 *   RPC function result type
 * @tparam Context
 *   RPC response context type
 */
final case class RpcResult[Result, Context](result: Result, context: Context)
