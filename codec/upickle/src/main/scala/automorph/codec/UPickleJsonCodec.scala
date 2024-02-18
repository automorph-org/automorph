package automorph.codec

import automorph.codec.UPickleJsonCodec.JsonConfig
import automorph.codec.json.{UPickleJsonRpc, UPickleOpenApi, UPickleOpenRpc, UPickleWebRpc}
import automorph.codec.meta.UPickleJsonMeta
import automorph.schema.{OpenApi, OpenRpc}
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
final case class UPickleJsonCodec[Config <: JsonConfig](config: Config = new JsonConfig {})
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

  /** uPickle reader and writer instances providing basic null-safe data types support for JSON format. */
  trait JsonConfig extends UPickleNullSafeConfig {

    implicit lazy val jsonRpcMessageRw: ReadWriter[UPickleJsonRpc.RpcMessage] = UPickleJsonRpc.readWriter(this)
    implicit lazy val restRpcMessageRw: ReadWriter[UPickleWebRpc.RpcMessage] = UPickleWebRpc.readWriter(this)
    implicit lazy val openRpcRw: ReadWriter[OpenRpc] = UPickleOpenRpc.readWriter(this)
    implicit lazy val openApiRw: ReadWriter[OpenApi] = UPickleOpenApi.readWriter(this)
  }
}
