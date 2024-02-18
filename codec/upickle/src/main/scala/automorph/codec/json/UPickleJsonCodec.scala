package automorph.codec.json

import automorph.codec.json.meta.UPickleJsonMeta
import ujson.Value

/**
 * uPickle JSON message codec plugin.
 *
 * @see
 *   [[https://www.json.org Message format]]
 * @see
 *   [[https://github.com/com-lihaoyi/upickle Library documentation]]
 * @see
 *   [[https://com-lihaoyi.github.io/upickle/#uJson Node type]]
 * @constructor
 *   Creates an uPickle codec plugin using JSON as message format.
 * @param config
 *   Upickle configuration containing implicit reader and writer instances
 * @tparam Config
 *   Upickle configuration type
 */
final case class UPickleJsonCodec[Config <: UPickleJsonConfig](config: Config = UPickleJsonConfig.default)
  extends UPickleJsonMeta[Config] {

  import config.*

  override val mediaType: String = "application/json"
  private val indent = 2

  override def serialize(node: Value): Array[Byte] =
    config.writeToByteArray(node)

  override def deserialize(data: Array[Byte]): Value =
    config.read[Value](data)

  override def text(node: Value): String =
    config.write(node, indent)
}

object UPickleJsonCodec {

  /** Message node type. */
  type Node = Value
}
