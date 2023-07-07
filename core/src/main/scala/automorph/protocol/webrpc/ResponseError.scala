package automorph.protocol.webrpc

import automorph.protocol.webrpc.Response.mandatory

/**
 * Web-RPC call response error.
 *
 * @param message
 *   error message
 * @param code
 *   error code
 */
private[automorph] final case class ResponseError(message: String, code: Option[Int]) {

  def formed: MessageError =
    MessageError(message = Some(message), code = code)
}

private[automorph] object ResponseError {

  def apply(error: MessageError): ResponseError = {
    val message = mandatory(error.message, "message")
    new ResponseError(message, error.code)
  }
}
