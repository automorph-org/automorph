package automorph.log

import java.nio.charset.StandardCharsets

/**
 * Request & response message logger.
 *
 * @param logger
 *   logger
 * @param defaultProtocol
 *   transport protocol
 */
final private[automorph] case class MessageLog(logger: Logger, defaultProtocol: String = "") {

  def sendingRequest(
    requestProperties: => Map[String, String],
    messageText: Option[String],
    protocol: String = defaultProtocol,
  ): Unit =
    logger.trace(s"Sending $protocol request", requestProperties ++ messageText.map(MessageLog.messageBody -> _))

  def sentRequest(requestProperties: => Map[String, String], protocol: String = defaultProtocol): Unit =
    logger.debug(s"Sent $protocol request", requestProperties)

  def failedSendRequest(
    error: Throwable,
    requestProperties: => Map[String, String],
    protocol: String = defaultProtocol,
  ): Unit =
    logger.error(s"Failed to send $protocol request", error, requestProperties)

  def receivingRequest(requestProperties: => Map[String, String], protocol: String = defaultProtocol): Unit =
    logger.debug(s"Receiving $protocol request", requestProperties)

  def receivedRequest(
    requestProperties: => Map[String, String],
    messageText: Option[String],
    protocol: String = defaultProtocol,
  ): Unit =
    logger.trace(s"Received $protocol request", requestProperties ++ messageText.map(MessageLog.messageBody -> _))

  def receivedConnection(requestProperties: => Map[String, String], protocol: String = defaultProtocol): Unit =
    logger.debug(s"Received $protocol connection", requestProperties)

  def failedReceiveRequest(
    error: Throwable,
    requestProperties: => Map[String, String],
    protocol: String = defaultProtocol,
  ): Unit =
    logger.error(s"Failed to receive $protocol request", error, requestProperties)

  def failedProcessRequest(
    error: Throwable,
    requestProperties: => Map[String, String],
    protocol: String = defaultProtocol,
  ): Unit =
    logger.error(s"Failed to process $protocol request", error, requestProperties)

  def sendingResponse(
    responseProperties: => Map[String, String],
    messageText: Option[String],
    protocol: String = defaultProtocol,
  ): Unit =
    logger.trace(s"Sending $protocol response", responseProperties ++ messageText.map(MessageLog.messageBody -> _))

  def sentResponse(responseProperties: => Map[String, String], protocol: String = defaultProtocol): Unit =
    logger.debug(s"Sent $protocol response", responseProperties)

  def failedSendResponse(
    error: Throwable,
    responseProperties: => Map[String, String],
    protocol: String = defaultProtocol,
  ): Unit =
    logger.error(s"Failed to send $protocol response", error, responseProperties)

  def receivingResponse(responseProperties: => Map[String, String], protocol: String = defaultProtocol): Unit =
    logger.trace(s"Receiving $protocol response", responseProperties)

  def receivedResponse(
    responseProperties: => Map[String, String],
    messageText: Option[String],
    protocol: String = defaultProtocol,
  ): Unit =
    logger.trace(s"Received $protocol response", responseProperties ++ messageText.map(MessageLog.messageBody -> _))

  def failedReceiveResponse(
    error: Throwable,
    responseProperties: => Map[String, String],
    protocol: String = defaultProtocol,
  ): Unit =
    logger.error(s"Failed to receive $protocol response", error, responseProperties)
}

private[automorph] object MessageLog {

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

  private val contentTypeTextPrefix = "text/"
  private val contentTypeJson = "application/json"

  /**
   * Extract textual representation of the message body if possible.
   *
   * @param body
   *   message body
   * @param contentType
   *   content MIME type
   * @return
   *   textual message body
   */
  def messageText(body: Array[Byte], contentType: Option[String]): Option[String] =
    contentType.filter { value =>
      value == contentTypeJson || value.startsWith(contentTypeTextPrefix)
    }.map(_ => new String(body, StandardCharsets.UTF_8))
}
