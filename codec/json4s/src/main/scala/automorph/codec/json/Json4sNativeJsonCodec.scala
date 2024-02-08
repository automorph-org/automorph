package automorph.codec.json

import automorph.codec.json.meta.Json4sNativeJsonMeta
import automorph.schema.{OpenApi, OpenRpc}
import automorph.util.Extensions.StringOps
import org.json4s.{DefaultFormats, Formats, JValue}
import org.json4s.native.JsonMethods
import java.io.ByteArrayInputStream

/**
 * Json4s JSON message codec plugin.
 *
 * @see
 *   [[https://www.json.org Message format]]
 * @see
 *   [[https://json4s.org Library documentation]]
 * @see
 *   [[https://javadoc.io/doc/org.json4s/json4s-ast_2.13/latest/org/json4s/JValue.html Node type]]
 * @constructor
 *   Creates a Json4s codec plugin using JSON as message format.
 */
final case class Json4sNativeJsonCodec() extends Json4sNativeJsonMeta {

  override val mediaType: String = "application/json"

  override def serialize(node: JValue): Array[Byte] =
    JsonMethods.compact(JsonMethods.render(node)).toByteArray

  override def deserialize(data: Array[Byte]): JValue =
    JsonMethods.parse(new ByteArrayInputStream(data))

  override def text(node: JValue): String =
    JsonMethods.pretty(JsonMethods.render(node))
}

object Json4sNativeJsonCodec {

  /** Message node type. */
  type Node = JValue

  implicit val formats: Formats = DefaultFormats.strict

//  implicit lazy val jsonRpcMessageEncoder: Encoder[Json4sJsonRpc.RpcMessage] = Json4sJsonRpc.encoder
//  implicit lazy val jsonRpcMessageDecoder: Decoder[Json4sJsonRpc.RpcMessage] = Json4sJsonRpc.decoder
//  implicit lazy val restRpcMessageEncoder: Encoder[Json4sWebRpc.RpcMessage] = Json4sWebRpc.encoder
//  implicit lazy val restRpcMessageDecoder: Decoder[Json4sWebRpc.RpcMessage] = Json4sWebRpc.decoder
//  implicit lazy val openRpcEncoder: Encoder[OpenRpc] = Json4sOpenRpc.encoder
//  implicit lazy val openRpcDecoder: Decoder[OpenRpc] = Json4sOpenRpc.decoder
//  implicit lazy val openApiEncoder: Encoder[OpenApi] = Json4sOpenApi.encoder
//  implicit lazy val openApiDecoder: Decoder[OpenApi] = Json4sOpenApi.decoder
}
