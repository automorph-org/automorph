package automorph.client

import automorph.RpcFunction

/**
 * RPC client remote API function binding.
 *
 * Note: Consider this class to be private and do not use it. It remains public only due to Scala 2 macro limitations.
 *
 * @param function
 *   bound function descriptor
 * @param argumentEncoders
 *   map of method parameter names to argument encoding functions
 * @param decodeResult
 *   decodes bound function result
 * @param acceptsContext
 *   true if the last parameter of the bound function is contextual
 * @tparam Node
 *   message node type
 * @tparam Context
 *   RPC message context type
 */
final case class ClientBinding[Node, Context](
  function: RpcFunction,
  argumentEncoders: Map[String, Any => Node],
  decodeResult: (Node, Context) => Any,
  acceptsContext: Boolean,
)
