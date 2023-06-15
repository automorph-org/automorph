package automorph.spi.codec

/**
 * Structured message format codec code generation.
 *
 * @tparam Node
 *   message codec node representation type
 */
trait MessageCodecMeta[Node] {

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
  def encode[T](value: T): Node =
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
  def decode[T](node: Node): T =
    throw new UnsupportedOperationException("Macro not implemented")
}
