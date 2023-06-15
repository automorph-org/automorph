package automorph.protocol.jsonrpc

import scala.collection.immutable.ListMap

/**
 * JSON-RPC protocol message structure.
 *
 * @see
 *   [[https://www.jsonrpc.org/specification JSON-RPC protocol specification]]
 * @param jsonrpc
 *   protocol version (must be 2.0)
 * @param id
 *   call identifier, a request without and identifier is considered to be a notification
 * @param method
 *   invoked method name
 * @param params
 *   invoked method argument values by position or by name
 * @param result
 *   succesful method call result value
 * @param error
 *   failed method call error details
 * @tparam Node
 *   message node type
 */
final case class Message[Node](
  jsonrpc: Option[String],
  id: Option[Either[BigDecimal, String]],
  method: Option[String],
  params: Option[Message.Params[Node]],
  result: Option[Node],
  error: Option[MessageError[Node]],
) {

  /** Message type. */
  lazy val messageType: MessageType = error.map(_ => MessageType.Error).getOrElse {
    result.map(_ => MessageType.Result).getOrElse(id.map(_ => MessageType.Call).getOrElse(MessageType.Notification))
  }

  /** Message properties. */
  lazy val properties: Map[String, String] = ListMap("Type" -> messageType.toString) ++
    method.map(value => "Method" -> value) ++ params.map(value => "Arguments" -> value.fold(_.size, _.size).toString) ++
    error.toSeq.flatMap { value =>
      value.code.map(code => "Error Code" -> code.toString) ++ value.message.map(message => "ErrorMessage" -> message)
    } ++ id.map(value => "Identifier" -> value.fold(_.toString, identity))
}

case object Message {

  /** Message identifier type. */
  type Id = Either[BigDecimal, String]

  /** Request parameters type. */
  type Params[Node] = Either[List[Node], Map[String, Node]]

  /** Supported JSON-RPC protocol version. */
  val version = "2.0"
}

/**
 * JSON-RPC protocol message error structure.
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
final case class MessageError[Node](message: Option[String], code: Option[Int], data: Option[Node])

/** JSON-RPC message type. */
sealed abstract class MessageType {

  /** Message type name. */
  def name: String =
    toString
}

case object MessageType {

  /** JSON-RPC method call request. */
  case object Call extends MessageType

  /** JSON-RPC method notification request. */
  case object Notification extends MessageType

  /** JSON-RPC result response. */
  case object Result extends MessageType

  /** JSON-RPC error response. */
  case object Error extends MessageType
}
