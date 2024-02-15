package automorph.protocol.webrpc

import automorph.RpcException.InvalidResponse

/**
 * Web-RPC call response.
 *
 * @param result
 *   call result
 * @param error
 *   call error
 * @tparam Node
 *   message node type
 */
final private[automorph] case class Response[Node](result: Option[Node], error: Option[ResponseError]) {

  def message: Message[Node] =
    Message[Node](result = result, error = error.map(_.formed))
}

private[automorph] object Response {

  def apply[Node](message: Message[Node]): Response[Node] =
    message.result.map(result => Response(Some(result), None)).getOrElse {
      val error = mandatory(message.error, "error")
      Response(None, Some(ResponseError(error)))
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
