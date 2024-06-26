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
    properties: => Map[String, String],
    messageText: Option[String],
    protocol: String = defaultProtocol,
  ): Unit =
    logger.trace(s"Sending $protocol request", properties ++ messageText.map(MessageLog.messageBody -> _))

  def sentRequest(properties: => Map[String, String], protocol: String = defaultProtocol): Unit =
    logger.debug(s"Sent $protocol request", properties)

  def failedSendRequest(
    error: Throwable,
    properties: => Map[String, String],
    protocol: String = defaultProtocol,
  ): Unit =
    logger.error(s"Failed to send $protocol request", error, properties)

  def receivingRequest(properties: => Map[String, String], protocol: String = defaultProtocol): Unit =
    logger.debug(s"Receiving $protocol request", properties)

  def receivedRequest(
    properties: => Map[String, String],
    messageText: Option[String],
    protocol: String = defaultProtocol,
  ): Unit =
    logger.trace(s"Received $protocol request", properties ++ messageText.map(MessageLog.messageBody -> _))

  def acceptedListenConnection(properties: => Map[String, String], protocol: String = defaultProtocol): Unit =
    logger.debug(s"Accepted $protocol listen connection", properties)

  def establishedListenConnection(properties: => Map[String, String], protocol: String = defaultProtocol): Unit =
    logger.debug(s"Established $protocol listen connection", properties)

  def failedReceiveRequest(
    error: Throwable,
    properties: => Map[String, String],
    protocol: String = defaultProtocol,
  ): Unit =
    logger.error(s"Failed to receive $protocol request", error, properties)

  def failedProcessRequest(
    error: Throwable,
    properties: => Map[String, String],
    protocol: String = defaultProtocol,
  ): Unit =
    logger.error(s"Failed to process $protocol request", error, properties)

  def sendingResponse(
    properties: => Map[String, String],
    messageText: Option[String],
    protocol: String = defaultProtocol,
  ): Unit =
    logger.trace(s"Sending $protocol response", properties ++ messageText.map(MessageLog.messageBody -> _))

  def sentResponse(properties: => Map[String, String], protocol: String = defaultProtocol): Unit =
    logger.debug(s"Sent $protocol response", properties)

  def failedSendResponse(
    error: Throwable,
    properties: => Map[String, String],
    protocol: String = defaultProtocol,
  ): Unit =
    logger.error(s"Failed to send $protocol response", error, properties)

  def receivingResponse(properties: => Map[String, String], protocol: String = defaultProtocol): Unit =
    logger.trace(s"Receiving $protocol response", properties)

  def receivedResponse(
    properties: => Map[String, String],
    messageText: Option[String],
    protocol: String = defaultProtocol,
  ): Unit =
    logger.trace(s"Received $protocol response", properties ++ messageText.map(MessageLog.messageBody -> _))

  def failedReceiveResponse(
    error: Throwable,
    properties: => Map[String, String],
    protocol: String = defaultProtocol,
  ): Unit =
    logger.error(s"Failed to receive $protocol response", error, properties)
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
