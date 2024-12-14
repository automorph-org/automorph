package automorph

/**
 * RPC call.
 *
 * @param function
 *   function name
 * @param arguments
 *   function arguments
 * @tparam Context
 *   RPC message context type
 */
final case class RpcCall[Context](
  function: String,
  arguments: Seq[Any],
  context: Context,
)
