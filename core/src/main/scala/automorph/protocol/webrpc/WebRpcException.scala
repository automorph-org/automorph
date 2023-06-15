package automorph.protocol.webrpc

/**
 * Web-RPC API error exception.
 *
 * API methods bound via an RPC request handler can throw this exception to customize Web-RPC error response.
 *
 * @see
 *   [[https://automorph.org/rest-rpc Web-RPC protocol specification]]
 * @constructor
 *   Creates a new Web-RPC error exception.
 * @param message
 *   error message
 * @param code
 *   error code
 * @param cause
 *   exception cause
 */
final case class WebRpcException(message: String, code: Option[Int], cause: Throwable)
  extends RuntimeException(message, cause)
