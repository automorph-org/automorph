package automorph.protocol.jsonrpc

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
object ErrorType {

  case object ParseError extends ErrorType(-32700)

  case object InvalidRequest extends ErrorType(-32600)

  case object MethodNotFound extends ErrorType(-32601)

  case object InvalidParams extends ErrorType(-32602)

  case object InternalError extends ErrorType(-32603)

  case object ServerError extends ErrorType(-32000)

  case object ReservedError extends ErrorType(-32768)

  case object ApplicationError extends ErrorType(0)
}
