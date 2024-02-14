package automorph.codec.json.meta

import automorph.spi.MessageCodec
import org.json4s.{Extraction, Formats, JValue}
import scala.compiletime.summonInline

/** Json4s JSON codec plugin code generation. */
private[automorph] trait Json4sNativeJsonMeta extends MessageCodec[JValue]:

  val formats: Formats

  override inline def encode[T](value: T): JValue =
    implicit val formats: Formats = this.formats
    Extraction.decompose(value)

  override inline def decode[T](node: JValue): T =
    implicit val formats: Formats = this.formats
    Extraction.extract[T](node)(using formats, summonInline[Manifest[T]])
