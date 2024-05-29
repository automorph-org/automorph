package automorph.spi.codec

/**
 * Structured message format codec code generation.
 *
 * @tparam Value
 *   message codec node representation type
 */
trait MessageCodecMeta[Value] {

  /**
   * Encodes a value as a node.
   *
   * @param value
   *   value of given type
   * @tparam T
   *   value type
   * @return
   *   message codec node
   */
  def encode[T](value: T): Value =
    throw new UnsupportedOperationException("Macro not implemented")

  /**
   * Decodes a value from a node.
   *
   * @param node
   *   message codec node
   * @tparam T
   *   value type
   * @return
   *   value of the specified type
   */
  def decode[T](node: Value): T =
    throw new UnsupportedOperationException("Macro not implemented")
}
