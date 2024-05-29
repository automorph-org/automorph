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
 * @tparam Value
 *   message node type
 */
final case class Message[Value](
  jsonrpc: Option[String],
  id: Option[Either[BigDecimal, String]],
  method: Option[String],
  params: Option[Message.Params[Value]],
  result: Option[Value],
  error: Option[MessageError[Value]],
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

object Message {

  /** Message identifier type. */
  type Id = Either[BigDecimal, String]

  /** Request parameters type. */
  type Params[Value] = Either[List[Value], Map[String, Value]]

  /** Supported JSON-RPC protocol version. */
  val version = "2.0"

  private[automorph] val jsonrpc = "jsonrpc"
  private[automorph] val id = "id"
  private[automorph] val method = "method"
  private[automorph] val params = "params"
  private[automorph] val result = "result"
  private[automorph] val error = "error"
  private[automorph] val message = "message"
  private[automorph] val code = "code"
  private[automorph] val data = "data"
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
 * @tparam Value
 *   message node type
 */
final case class MessageError[Value](message: Option[String], code: Option[Int], data: Option[Value])

/** JSON-RPC message type. */
sealed trait MessageType {

  /** Message type name. */
  def name: String =
    toString
}

object MessageType {

  /** JSON-RPC method call request. */
  case object Call extends MessageType

  /** JSON-RPC method notification request. */
  case object Notification extends MessageType

  /** JSON-RPC result response. */
  case object Result extends MessageType

  /** JSON-RPC error response. */
  case object Error extends MessageType
}
