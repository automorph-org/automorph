package automorph.server

import automorph.RpcFunction

/**
 * RPC handler remote API function binding.
 *
 * Note: Consider this class to be private and do not use it. It remains public only due to Scala 2 macro limitations.
 *
 * @param function
 *   bound function descriptor
 * @param argumentDecoders
 *   map of method parameter names to argument decoding functions
 * @param encodeResult
 *   encodes bound function result
 * @param call
 *   calls bound function
 * @param acceptsContext
 *   true if the method accepts request context as its last parameter, false otherwise
 * @tparam Value
 *   message codec value representation type
 * @tparam Effect
 *   effect type
 * @tparam Context
 *   RPC message context type
 */
final case class ServerBinding[Value, Effect[_], Context](
  function: RpcFunction,
  argumentDecoders: Map[String, Option[Value] => Any],
  encodeResult: Any => (Value, Option[Context]),
  call: (Seq[Any], Context) => Any,
  acceptsContext: Boolean,
)
