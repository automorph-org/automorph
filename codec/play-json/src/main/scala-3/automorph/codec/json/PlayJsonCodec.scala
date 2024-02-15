package automorph.codec.json

import automorph.codec.json.meta.PlayJsonMeta
import play.api.libs.json.{JsValue, Json}

/**
 * Play JSON message codec plugin.
 *
 * @see
 *   [[https://www.json.org JSON message format]]
 * @see
 *   [[https://www.playframework.com/documentation/latest/ScalaJson Library documentation]]
 * @see
 *   [[https://www.playframework.com/documentation/latest/api/scala/play/api/libs/json/JsValue.html Node type]]
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

  /** Message node type. */
  type Node = JsValue
}
