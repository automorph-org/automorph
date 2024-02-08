package automorph.codec.json.meta

import automorph.codec.json.Json4sNativeJsonCodec
import automorph.spi.MessageCodec
import org.json4s.{Extraction, Formats, JValue}
import org.json4s.native.JsonMethods.*
import org.json4s.*
import scala.compiletime.summonInline

/** Json4s JSON codec plugin code generation. */
private[automorph] trait Json4sNativeJsonMeta extends MessageCodec[JValue]:

  @scala.annotation.nowarn("msg=unused import")
  override inline def encode[T](value: T): JValue =
    import Json4sNativeJsonCodec.given
    Extraction.decompose(value)(using summonInline[Formats])

  @scala.annotation.nowarn("msg=unused import")
  override inline def decode[T](node: JValue): T =
    import Json4sNativeJsonCodec.given
    Extraction.extract[T](node)(using summonInline[Formats], summonInline[Manifest[T]])
