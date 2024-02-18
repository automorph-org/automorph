package automorph.protocol.webrpc

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

  case object InvalidRequest extends ErrorType {

    override val code: Int =
      -32600
  }

  case object FunctionNotFound extends ErrorType {

    override val code: Int =
      -32601
  }

  case object InvalidArguments extends ErrorType {

    override val code: Int =
      -32602
  }

  case object ServerError extends ErrorType {

    override val code: Int =
      -32603
  }

  case object ApplicationError extends ErrorType {

    override val code: Int =
      0
  }
}
