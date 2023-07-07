package automorph.protocol.webrpc

import scala.collection.immutable.ListMap

/**
 * Web-RPC protocol message structure.
 *
 * @see
 *   [[https://automorph.org/rest-rpc Web-RPC protocol specification]]
 * @param result
 *   succesful method call result value
 * @param error
 *   failed method call error details
 * @tparam Node
 *   message node type
 */
final case class Message[Node](result: Option[Node], error: Option[MessageError]) {

  /** Message type. */
  lazy val messageType: MessageType = error.map(_ => MessageType.Error)
    .getOrElse(result.map(_ => MessageType.Result).getOrElse(MessageType.Call))

  /** Message properties. */
  lazy val properties: Map[String, String] = ListMap("Type" -> messageType.toString) ++ error.toSeq.flatMap { value =>
    value.code.map(code => "Error Code" -> code.toString) ++ value.message.map(message => "ErrorMessage" -> message)
  }
}

object Message {

  /** Request parameters type. */
  type Request[Node] = Map[String, Node]
}

/**
 * Web-RPC protocol message error structure.
 *
 * @see
 *   [[https://www.jsonrpc.org/specification Web-RPC protocol specification]]
 * @param message
 *   error message
 * @param code
 *   error code
 */
final case class MessageError(message: Option[String], code: Option[Int])

/** Web-RPC message type. */
sealed abstract class MessageType {

  /** Message type name. */
  def name: String =
    toString
}

object MessageType {

  /** Web-RPC function call request. */
  case object Call extends MessageType

  /** Web-RPC result response. */
  case object Result extends MessageType

  /** Web-RPC error response. */
  case object Error extends MessageType
}
