package automorph.protocol.jsonrpc

import automorph.protocol.jsonrpc.Response.mandatory

/**
 * JSON-RPC call response error.
 *
 * @see
 *   [[https://www.jsonrpc.org/specification JSON-RPC protocol specification]]
 * @param message
 *   error message
 * @param code
 *   error code
 * @param data
 *   additional error information
 * @tparam Value
 *   message node type
 */
final private[automorph] case class ResponseError[Value](message: String, code: Int, data: Option[Value]) {

  def formed: MessageError[Value] =
    MessageError[Value](message = Some(message), code = Some(code), data = data)
}

private[automorph] object ResponseError {

  private[automorph] def apply[Value](error: MessageError[Value]): ResponseError[Value] = {
    val message = mandatory(error.message, "message")
    val code = mandatory(error.code, "code")
    new ResponseError(message, code, error.data)
  }
}
