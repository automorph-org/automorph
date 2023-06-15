package automorph.protocol.webrpc

/**
 * JSON-RPC error type with code.
 *
 * @see
 *   [[https://www.jsonrpc.org/specification JSON-RPC protocol specification]]
 */
sealed abstract class ErrorType(val code: Int)

/**
 * JSON-RPC error types with codes.
 *
 * @see
 *   [[https://www.jsonrpc.org/specification JSON-RPC protocol specification]]
 */
case object ErrorType {

  case object InvalidRequest extends ErrorType(-32600)

  case object FunctionNotFound extends ErrorType(-32601)

  case object InvalidArguments extends ErrorType(-32602)

  case object ServerError extends ErrorType(-32603)

  case object ApplicationError extends ErrorType(0)
}
