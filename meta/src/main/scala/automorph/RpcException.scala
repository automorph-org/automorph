package automorph

/**
 * RPC exception.
 *
 * @param message error message
 * @param cause error cause
 */
sealed abstract class RpcException(message: String, cause: Throwable = None.orNull)
  extends RuntimeException(message, cause)

object RpcException {

  /** Invalid RPC request error. */
  final case class InvalidRequest(message: String, cause: Throwable = None.orNull)
    extends RpcException(message, cause)

  /** Invalid RPC response error. */
  final case class InvalidResponse(message: String, cause: Throwable = None.orNull)
    extends RpcException(message, cause)

  /** Remote function not found error. */
  final case class FunctionNotFound(message: String, cause: Throwable = None.orNull)
    extends RpcException(message, cause)

  /** Invalid remote function arguments error. */
  final case class InvalidArguments(message: String, cause: Throwable = None.orNull)
    extends RpcException(message, cause)

  /** Remote server error. */
  final case class ServerError(message: String, cause: Throwable = None.orNull)
    extends RpcException(message, cause)

  /** Remote API application error. */
  final case class ApplicationError(message: String, cause: Throwable = None.orNull)
    extends RpcException(message, cause)
}
