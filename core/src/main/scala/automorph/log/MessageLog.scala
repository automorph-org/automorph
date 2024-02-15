package automorph.log

/**
 * Request & response message logger.
 *
 * @param logger
 *   logger
 * @param defaultProtocol
 *   transport protocol
 */
final private[automorph] case class MessageLog(logger: Logger, defaultProtocol: String) {

  def sendingRequest(requestProperties: => Map[String, String], protocol: String = defaultProtocol): Unit =
    logger.trace(s"Sending $protocol request", requestProperties)

  def sentRequest(requestProperties: => Map[String, String], protocol: String = defaultProtocol): Unit =
    logger.debug(s"Sent $protocol request", requestProperties)

  def failedSendRequest(
    error: Throwable,
    requestProperties: => Map[String, String],
    protocol: String = defaultProtocol,
  ): Unit =
    logger.error(s"Failed to send $protocol request", error, requestProperties)

  def receivingRequest(requestProperties: => Map[String, String], protocol: String = defaultProtocol): Unit =
    logger.trace(s"Receiving $protocol request", requestProperties)

  def receivedRequest(requestProperties: => Map[String, String], protocol: String = defaultProtocol): Unit =
    logger.debug(s"Received $protocol request", requestProperties)

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

  def sendingResponse(responseProperties: => Map[String, String], protocol: String = defaultProtocol): Unit =
    logger.trace(s"Sending $protocol response", responseProperties)

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

  def receivedResponse(responseProperties: => Map[String, String], protocol: String = defaultProtocol): Unit =
    logger.debug(s"Received $protocol response", responseProperties)

  def failedReceiveResponse(
    error: Throwable,
    responseProperties: => Map[String, String],
    protocol: String = defaultProtocol,
  ): Unit =
    logger.error(s"Failed to receive $protocol response", error, responseProperties)
}
