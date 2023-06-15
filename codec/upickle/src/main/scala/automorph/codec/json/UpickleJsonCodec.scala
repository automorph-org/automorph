package automorph.codec.json

import automorph.codec.json.meta.UpickleJsonMeta
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
 * @param custom
 *   customized Upickle reader and writer implicits instance
 * @tparam Custom
 *   customized Upickle reader and writer implicits instance type
 */
final case class UpickleJsonCodec[Custom <: UpickleJsonCustom](custom: Custom = UpickleJsonCustom.default)
  extends UpickleJsonMeta[Custom] {

  import custom.*

  override val mediaType: String = "application/json"
  private val indent = 2

  override def serialize(node: Value): Array[Byte] =
    custom.writeToByteArray(node)

  override def deserialize(data: Array[Byte]): Value =
    custom.read[Value](data)

  override def text(node: Value): String =
    custom.write(node, indent)
}

case object UpickleJsonCodec {

  /** Message node type. */
  type Node = Value
}
