package automorph.spi.protocol

import automorph.RpcFunction

/**
 * RPC API description operation.
 *
 * @constructor
 *   Creates RPC API description operation.
 * @param function
 *   RPC function description
 * @param describe
 *   creates API description for specified RPC functions and RPC request metadata
 * @tparam Node
 *   message node type
 */
final case class ApiSchema[Node](
  function: RpcFunction,
  describe: Iterable[RpcFunction] => Node,
)
