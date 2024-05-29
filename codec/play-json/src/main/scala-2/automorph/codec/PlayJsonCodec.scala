package automorph.codec

import automorph.codec.json.{PlayJsonJsonRpc, PlayJsonOpenApi, PlayJsonOpenRpc, PlayJsonWebRpc}
import automorph.codec.meta.PlayJsonMeta
import automorph.schema.{OpenApi, OpenRpc}
import play.api.libs.json.{JsError, JsNull, JsObject, JsSuccess, JsValue, Json, Reads, Writes}

/**
 * Play JSON message codec plugin.
 *
 * @see
 *   [[https://www.json.org JSON message format]]
 * @see
 *   [[https://www.playframework.com/documentation/latest/ScalaJson Library documentation]]
 * @see
 *   [[https://www.playframework.com/documentation/latest/api/scala/play/api/libs/json/JsValue.html Value type]]
 * @constructor
 *   Creates an Play JSON codec plugin.
 */
final case class PlayJsonCodec() extends PlayJsonMeta {

  override val mediaType: String =
    "application/json"

  override def serialize(node: JsValue): Array[Byte] =
    Json.toBytes(node)

  override def deserialize(data: Array[Byte]): JsValue =
    Json.parse(data)

  override def text(node: JsValue): String =
    Json.prettyPrint(node)
}

object PlayJsonCodec {

  implicit def optionReads[T: Reads]: Reads[Option[T]] = {
    case JsNull => JsSuccess(None)
    case value => value.validate[T].map(Some.apply)
  }

  implicit def optionWrites[T: Writes]: Writes[Option[T]] =
    (optionalValue: Option[T]) => optionalValue.map(value => implicitly[Writes[T]].writes(value)).getOrElse(JsNull)

  /** Message node type. */
  type Value = JsValue
  implicit lazy val unitReads: Reads[Unit] = {
    case value: JsObject if value.values.isEmpty => JsSuccess.apply(())
    case _ => JsError.apply("JSON object representing Unit type must be empty")
  }
  implicit lazy val unitWrites: Writes[Unit] = (_: Unit) => JsObject(Seq())
  implicit lazy val jsonRpcReads: Reads[PlayJsonJsonRpc.RpcMessage] = PlayJsonJsonRpc.reads
  implicit lazy val jsonRpcWrites: Writes[PlayJsonJsonRpc.RpcMessage] = PlayJsonJsonRpc.writes
  implicit lazy val webRpcReads: Reads[PlayJsonWebRpc.RpcMessage] = PlayJsonWebRpc.reads
  implicit lazy val webRpcWrites: Writes[PlayJsonWebRpc.RpcMessage] = PlayJsonWebRpc.writes
  implicit lazy val openApiReads: Reads[OpenApi] = PlayJsonOpenApi.reads
  implicit lazy val openApiWrites: Writes[OpenApi] = PlayJsonOpenApi.writes
  implicit lazy val openRpcReads: Reads[OpenRpc] = PlayJsonOpenRpc.reads
  implicit lazy val openRpcWrites: Writes[OpenRpc] = PlayJsonOpenRpc.writes
}
