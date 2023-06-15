package automorph.spi.codec

/**
 * Structured message format codec code generation.
 *
 * @tparam Node
 *   message codec node representation type
 */
trait MessageCodecMeta[Node]:

  /**
   * Encodes a value as a message codec node.
   *
   * @param value
   *   value of given type
   * @tparam T
   *   value type
   * @return
   *   message codec node
   */
  inline def encode[T](value: T): Node

  /**
   * Decodes a value from a message codec node.
   *
   * @param node
   *   message codec node
   * @tparam T
   *   value type
   * @return
   *   value of the specified type
   */
  inline def decode[T](node: Node): T
