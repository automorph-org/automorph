package automorph.codec.messagepack

import automorph.codec.UPickleNullSafeConfig
import automorph.codec.messagepack.UPickleMessagePackCodec.MessagePackConfig
import automorph.codec.messagepack.meta.UPickleMessagePackMeta
import automorph.schema.{OpenApi, OpenRpc}
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
final case class UPickleMessagePackCodec[Config <: MessagePackConfig](
  config: Config = new MessagePackConfig {}
) extends UPickleMessagePackMeta[Config] {

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

object UPickleMessagePackCodec {

  /** Message node type. */
  type Node = Msg

  /** uPickle reader and writer instances providing basic null-safe data types support for MessagePack format. */
  trait MessagePackConfig extends UPickleNullSafeConfig {

    implicit lazy val jsonRpcMessageRw: ReadWriter[UPickleJsonRpc.RpcMessage] = UPickleJsonRpc.readWriter(this)
    implicit lazy val restRpcMessageRw: ReadWriter[UPickleWebRpc.RpcMessage] = UPickleWebRpc.readWriter(this)
    implicit lazy val openRpcRw: ReadWriter[OpenRpc] = UPickleOpenRpc.readWriter(this)
    implicit lazy val openApiRw: ReadWriter[OpenApi] = UPickleOpenApi.readWriter(this)
  }
}
