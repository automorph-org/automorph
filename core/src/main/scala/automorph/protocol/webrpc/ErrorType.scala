package automorph.protocol.webrpc

/**
 * Web-RPC error type with code.
 *
 * @see
 *   [[https://automorph.org/docs/Web-RPC Web-RPC protocol specification]]
 */
sealed trait ErrorType {

  /** Error code. */
  def code: Int
}

/**
 * Web-RPC error types with codes.
 *
 * @see
 *   [[https://automorph.org/docs/Web-RPC Web-RPC protocol specification]]
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
