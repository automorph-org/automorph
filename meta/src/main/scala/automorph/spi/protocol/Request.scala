package automorph.spi.protocol

/**
 * RPC request.
 *
 * @constructor
 *   Creates RPC request.
 * @param function
 *   invoked function name
 * @param arguments
 *   invoked function arguments by name or by position
 * @param respond
 *   true if this request mandates a response, false if there should be no response
 * @param message
 *   RPC message
 * @param id
 *   request correlation identifier
 * @tparam Value
 *   message codec value representation type
 * @tparam Metadata
 *   protocol-specific message metadata type
 * @tparam Context
 *   message context type
 */
final case class Request[Value, Metadata, Context](
  function: String,
  arguments: Seq[Either[Value, (String, Value)]],
  respond: Boolean,
  context: Context,
  message: Message[Metadata],
  id: String,
)
