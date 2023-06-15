package automorph.codec.messagepack

import automorph.codec.messagepack.meta.UpickleMessagePackMeta
import upack.Msg

/**
 * uPickle MessagePack message codec plugin.
 *
 * @see
 *   [[https://msgpack.org Message format]]
 * @see
 *   [[https://github.com/com-lihaoyi/upickle Library documentation]]
 * @see
 *   [[https://com-lihaoyi.github.io/upickle/#uPack Node type]]
 * @constructor
 *   Creates a uPickle codec plugin using MessagePack as message format.
 * @param custom
 *   customized Upickle reader and writer implicits instance
 * @tparam Custom
 *   customized Upickle reader and writer implicits instance type
 */
final case class UpickleMessagePackCodec[Custom <: UpickleMessagePackCustom](
  custom: Custom = UpickleMessagePackCustom.default
) extends UpickleMessagePackMeta[Custom] {

  import custom.*

  override val mediaType: String = "application/msgpack"
  private val indent = 2

  override def serialize(node: Msg): Array[Byte] =
    custom.writeBinary(node)

  override def deserialize(data: Array[Byte]): Msg =
    custom.readBinary[Msg](data)

  override def text(node: Msg): String =
    custom.write(node, indent)
}

case object UpickleMessagePackCodec {

  /** Message node type. */
  type Node = Msg
}
