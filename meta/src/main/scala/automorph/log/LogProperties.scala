package automorph.log

/** RPC message logging properties. */
private[automorph] object LogProperties {

  /** Request correlation identifier. */
  val requestId = "Request ID"

  /** Message body */
  val messageBody = "Message Body"

  /** Client address. */
  val client = "Client"

  /** Request URL. */
  val url = "URL"

  /** Transport protocol. */
  val protocol = "Protocol"

  /** HTTP method. */
  val method = "Method"

  /** HTTP status code. */
  val status = "Status"
}
