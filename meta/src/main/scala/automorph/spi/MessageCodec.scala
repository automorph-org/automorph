package automorph.spi

import automorph.spi.codec.MessageCodecMeta

/**
 * Structured message format codec plugin.
 *
 * Enables serialization of RPC messages into specific structured data format.
 *
 * The underlying data format must support arbitrarily nested structures of basic data types. Given library must provide
 * intermediate representation of structured data (i.e. document object model).
 *
 * @tparam Value
 *   message format node type
 */
trait MessageCodec[Value] extends MessageCodecMeta[Value] {

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
  def serialize(node: Value): Array[Byte]

  /**
   * Deserializes a node from binary data.
   *
   * @param data
   *   binary data in the specific codec
   * @return
   *   node
   */
  def deserialize(data: Array[Byte]): Value

  /**
   * Formats a node as human-readable text.
   *
   * @param node
   *   node
   * @return
   *   node in human-readable textual form
   */
  def text(node: Value): String
}

private[automorph] object MessageCodec {

  /** Encode method name. */
  val encodeMethod: String = "encode"

  /** Decode method name. */
  val decodeMethod: String = "decode"
}
