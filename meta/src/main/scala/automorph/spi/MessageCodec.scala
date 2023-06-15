package automorph.spi

import automorph.spi.codec.MessageCodecMeta

/**
 * Structured message format codec plugin.
 *
 * The underlying data format must support storing arbitrarily nested structures of basic data types.
 *
 * @tparam Node
 *   message format node type
 */
trait MessageCodec[Node] extends MessageCodecMeta[Node] {

  /** Message format media (MIME) type. */
  def mediaType: String

  /**
   * Serializes a node as binary data.
   *
   * @param node
   *   node
   * @return
   *   binary data in the specific codec
   */
  def serialize(node: Node): Array[Byte]

  /**
   * Deserializes a node from binary data.
   *
   * @param data
   *   binary data in the specific codec
   * @return
   *   node
   */
  def deserialize(data: Array[Byte]): Node

  /**
   * Formats a node as human-readable text.
   *
   * @param node
   *   node
   * @return
   *   node in human-readable textual form
   */
  def text(node: Node): String
}

private[automorph] case object MessageCodec {

  /** Encode method name. */
  val encodeMethod: String = "encode"

  /** Decode method name. */
  val decodeMethod: String = "decode"
}
