package automorph.protocol.jsonrpc

import automorph.RpcException.InvalidResponse
import automorph.protocol.jsonrpc.Message.{Id, version}

/**
 * JSON-RPC call response.
 *
 * @see
 *   [[https://www.jsonrpc.org/specification JSON-RPC protocol specification]]
 * @param id
 *   call identifier
 * @param result
 *   call result
 * @param error
 *   call error
 * @tparam Value
 *   message codec value representation type
 */
final private[automorph] case class Response[Value](id: Id, result: Option[Value], error: Option[ResponseError[Value]]) {

  def message: Message[Value] =
    Message[Value](
      jsonrpc = Some(version),
      id = Some(id),
      method = None,
      params = None,
      result = result,
      error = error.map(_.formed),
    )
}

private[automorph] object Response {

  def apply[Value](message: Message[Value]): Response[Value] = {
    val jsonrpc = mandatory(message.jsonrpc, Message.jsonrpc)
    if (jsonrpc != version) {
      throw InvalidResponse(s"Invalid JSON-RPC protocol version: $jsonrpc", None.orNull)
    }
    val id = mandatory(message.id, Message.id)
    message.result.map(result => Response(id, Some(result), None)).getOrElse {
      val error = mandatory(message.error, Message.error)
      Response(id, None, Some(ResponseError(error)))
    }
  }

  /**
   * Return specified mandatory property value or throw an exception if it is missing.
   *
   * @param value
   *   property value
   * @param name
   *   property name
   * @tparam T
   *   property type
   * @return
   *   property value
   * @throws InvalidResponse
   *   if the property value is missing
   */
  def mandatory[T](value: Option[T], name: String): T =
    value.getOrElse(throw InvalidResponse(s"Missing message property: $name", None.orNull))
}
