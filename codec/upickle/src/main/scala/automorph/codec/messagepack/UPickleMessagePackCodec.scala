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
 * @param config
 *   Upickle configuration containing implicit reader and writer instances
 * @tparam Config
 *   Upickle configuration type
 */
final case class UpickleMessagePackCodec[Config <: UpickleMessagePackConfig](
  config: Config = UpickleMessagePackConfig.default
) extends UpickleMessagePackMeta[Config] {

  import config.*

  override val mediaType: String = "application/msgpack"
  private val indent = 2

  override def serialize(node: Msg): Array[Byte] =
    config.writeBinary(node)

  override def deserialize(data: Array[Byte]): Msg =
    config.readBinary[Msg](data)

  override def text(node: Msg): String =
    config.write(node, indent)
}

case object UpickleMessagePackCodec {

  /** Message node type. */
  type Node = Msg
}