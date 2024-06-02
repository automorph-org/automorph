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
 * @param id
 *   request correlation identifier
 * @tparam Value
 *   message codec value representation type
 * @tparam Content
 *   protocol-specific message content type
 */
final case class Response[Value, Content](
  result: Try[Value],
  message: Message[Content],
  id: String,
)
