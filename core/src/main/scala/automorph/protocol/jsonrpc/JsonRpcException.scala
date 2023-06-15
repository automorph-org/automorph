package automorph.protocol.jsonrpc

/**
 * JSON-RPC API error exception.
 *
 * API methods bound via an RPC request handler can throw this exception to customize JSON-RPC error response.
 *
 * @see
 *   [[https://www.jsonrpc.org/specification JSON-RPC protocol specification]]
 * @constructor
 *   Creates a new JSON-RPC error exception.
 * @param message
 *   error description
 * @param code
 *   error code
 * @param data
 *   additional error information
 * @param cause
 *   exception cause
 * @tparam Node
 *   message codec node representation type
 */
final case class JsonRpcException[Node](
  message: String,
  code: Int,
  data: Option[Node] = None,
  cause: Throwable = None.orNull,
) extends RuntimeException(message, cause)
