package automorph.protocol.jsonrpc

import automorph.RpcException.{
  ApplicationError, FunctionNotFound, InvalidArguments, InvalidRequest,
  ServerError,
}

/** JSON-RPC protocol errors. */
private[automorph] trait ErrorMapping {

  /**
   * Maps a JSON-RPC error to a corresponding default exception.
   *
   * @param message
   *   error message
   * @param code
   *   error code
   * @return
   *   exception
   */
  def defaultMapError(message: String, code: Int): Throwable =
    code match {
      case ErrorType.ParseError.code => InvalidRequest(message)
      case ErrorType.InvalidRequest.code => InvalidRequest(message)
      case ErrorType.MethodNotFound.code => FunctionNotFound(message)
      case ErrorType.InvalidParams.code => InvalidArguments(message)
      case ErrorType.InternalError.code => ServerError(message)
      case _ if Range.inclusive(ErrorType.ReservedError.code, ErrorType.ServerError.code).contains(code) =>
        ServerError(message)
      case _ => ApplicationError(message)
    }

  /**
   * Maps an exception to a corresponding default JSON-RPC error type.
   *
   * @param exception
   *   exception
   * @return
   *   JSON-RPC error type
   */
  def defaultMapException(exception: Throwable): ErrorType =
    exception match {
      case _: InvalidRequest => ErrorType.InvalidRequest
      case _: FunctionNotFound => ErrorType.MethodNotFound
      case _: InvalidArguments => ErrorType.InvalidParams
      case _: IllegalArgumentException => ErrorType.InvalidParams
      case _: ServerError => ErrorType.ServerError
      case _ => ErrorType.ApplicationError
    }
}
