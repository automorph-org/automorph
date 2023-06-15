package automorph.spi.protocol

import scala.util.Try

/**
 * RPC response.
 *
 * @constructor
 *   Creates RPC response.
 * @param result
 *   a call result on success or an exception on failure
 * @param message
 *   RPC message
 * @tparam Node
 *   message node type
 * @tparam Content
 *   protocol-specific message content type
 */
final case class Response[Node, Content](
  result: Try[Node],
  message: Message[Content],
)
