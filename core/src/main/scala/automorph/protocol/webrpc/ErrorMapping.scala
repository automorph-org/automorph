package automorph.protocol.webrpc

import automorph.RpcException.{ApplicationError, FunctionNotFound, InvalidArguments, InvalidRequest, ServerError}

private[automorph] trait ErrorMapping {

  /**
   * Maps a Web-RPC error to a corresponding default exception.
   *
   * @param message
   *   error message
   * @param code
   *   error code
   * @return
   *   exception
   */
  def defaultMapError(message: String, code: Option[Int]): Throwable =
    code match {
      case Some(ErrorType.InvalidRequest.code) => InvalidRequest(message)
      case Some(ErrorType.FunctionNotFound.code) => FunctionNotFound(message)
      case Some(ErrorType.InvalidArguments.code) => InvalidArguments(message)
      case Some(ErrorType.ServerError.code) => ServerError(message)
      case _ => ApplicationError(message)
    }

  /**
   * Maps an exception to a corresponding default Web-RPC error type.
   *
   * @param exception
   *   exception
   * @return
   *   Web-RPC error type
   */
  def defaultMapException(exception: Throwable): ErrorType =
    exception match {
      case _: InvalidRequest => ErrorType.InvalidRequest
      case _: FunctionNotFound => ErrorType.FunctionNotFound
      case _: InvalidArguments => ErrorType.InvalidArguments
      case _: IllegalArgumentException => ErrorType.InvalidArguments
      case _: ServerError => ErrorType.ServerError
      case _ => ErrorType.ApplicationError
    }
}
