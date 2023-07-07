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
 * @tparam Node
 *   message node type
 */
private[automorph] final case class ResponseError[Node](message: String, code: Int, data: Option[Node]) {

  def formed: MessageError[Node] =
    MessageError[Node](message = Some(message), code = Some(code), data = data)
}

private[automorph] object ResponseError {

  private[automorph] def apply[Node](error: MessageError[Node]): ResponseError[Node] = {
    val message = mandatory(error.message, "message")
    val code = mandatory(error.code, "code")
    new ResponseError(message, code, error.data)
  }
}
