package automorph.codec.json

import argonaut.Argonaut.{jNull, StringToParseWrap}
import argonaut.{CodecJson, DecodeResult, Json}
import automorph.codec.json.meta.ArgonautJsonMeta
import automorph.schema.{OpenApi, OpenRpc}
import automorph.util.Extensions.{ByteArrayOps, StringOps}

/**
 * Argonaut JSON message codec plugin.
 *
 * @see
 *   [[https://www.json.org Message format]]
 * @see
 *   [[http://argonaut.io/doc Library documentation]]
 * @see
 *   [[http://argonaut.io/scaladocs/#argonaut.Json Node type]]
 * @constructor
 *   Creates an Argonaut codec plugin using JSON as message format.
 */
final case class ArgonautJsonCodec() extends ArgonautJsonMeta {

  override val mediaType: String = "application/json"

  override def serialize(node: Json): Array[Byte] =
    node.nospaces.toByteArray

  override def deserialize(data: Array[Byte]): Json =
    data.asString.decodeEither[Json].fold(errorMessage => throw new IllegalArgumentException(errorMessage), identity)

  override def text(node: Json): String =
    node.spaces2
}

case object ArgonautJsonCodec {

  /** Message node type. */
  type Node = Json

  implicit lazy val noneCodecJson: CodecJson[None.type] = CodecJson(
    (_: None.type) => jNull,
    cursor => if (cursor.focus.isNull) DecodeResult.ok(None) else DecodeResult.fail("Not a null", cursor.history),
  )

  implicit lazy val jsonRpcMessageCodecJson: CodecJson[ArgonautJsonRpc.RpcMessage] = ArgonautJsonRpc.messageCodecJson

  implicit lazy val restRpcMessageCodecJson: CodecJson[ArgonautWebRpc.RpcMessage] = ArgonautWebRpc.messageCodecJson

  implicit lazy val openRpcCodecJson: CodecJson[OpenRpc] = ArgonautOpenRpc.openRpcCodecJson

  implicit lazy val openApiCodecJson: CodecJson[OpenApi] = ArgonautOpenApi.openApiCodecJson
}
