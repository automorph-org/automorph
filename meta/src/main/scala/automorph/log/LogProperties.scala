package automorph.log

/** RPC message logging properties. */
private[automorph] case object LogProperties {

  /** Request correlation identifier */
  val requestId = "Request ID"

  /** Message body */
  val messageBody = "Message Body"

  /** Client address. */
  val client = "Client"
}
