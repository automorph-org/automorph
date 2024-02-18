package automorph.protocol.jsonrpc

/**
 * JSON-RPC error type with code.
 *
 * @see
 *   [[https://www.jsonrpc.org/specification JSON-RPC protocol specification]]
 */
sealed trait ErrorType {

  /** Error code. */
  def code: Int
}

/**
 * JSON-RPC error types with codes.
 *
 * @see
 *   [[https://www.jsonrpc.org/specification JSON-RPC protocol specification]]
 */
object ErrorType {

  case object ParseError extends ErrorType {
    override val code: Int =
      -32700
  }

  case object InvalidRequest extends ErrorType {
    override val code: Int =
      -32600
  }

  case object MethodNotFound extends ErrorType {
    override val code: Int =
      -32601
  }

  case object InvalidParams extends ErrorType {
    override val code: Int =
      -32602
  }

  case object InternalError extends ErrorType {
    override val code: Int =
      -32603
  }

  case object ServerError extends ErrorType {
    override val code: Int =
      -32000
  }

  case object ReservedError extends ErrorType {
    override val code: Int =
      -32768
  }

  case object ApplicationError extends ErrorType {
    override val code: Int =
      0
  }
}
