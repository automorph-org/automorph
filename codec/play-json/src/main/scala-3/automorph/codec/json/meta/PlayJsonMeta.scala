package automorph.codec.json.meta

import automorph.codec.json.PlayJsonCodec
import automorph.spi.MessageCodec
import play.api.libs.json.{JsResult, JsValue, Json, Reads, Writes}
import scala.compiletime.summonInline

/** Play JSON codec plugin code generation. */
private[automorph] trait PlayJsonMeta extends MessageCodec[JsValue]:

  @scala.annotation.nowarn("msg=unused import")
  override inline def encode[T](value: T): JsValue =
    import PlayJsonCodec.given
    Json.toJson(value)(using summonInline[Writes[T]])

  @scala.annotation.nowarn("msg=unused import")
  override inline def decode[T](node: JsValue): T =
    import PlayJsonCodec.given
    JsResult.toTry(Json.fromJson(node)(using summonInline[Reads[T]]), JsResult.Exception.apply).get
