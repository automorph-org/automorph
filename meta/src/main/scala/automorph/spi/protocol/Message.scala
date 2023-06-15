package automorph.spi.protocol

/**
 * RPC message.
 *
 * @constructor
 *   Creates RPC message.
 * @param metadata
 *   protocol-specific message metadata
 * @param body
 *   message body
 * @param properties
 *   message properties
 * @tparam Metadata
 *   protocol-specific message metadata type
 */
final case class Message[Metadata](
  metadata: Metadata,
  body: Array[Byte],
  properties: Map[String, String] = Map.empty,
  private val messageText: () => Option[String] = () => None,
) {

  /** Message in human-readable textual form. */
  lazy val text: Option[String] = messageText()
}
