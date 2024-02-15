package automorph.codec.json

import automorph.codec.json.meta.Json4sNativeJsonMeta
import automorph.util.Extensions.StringOps
import org.json4s.{CustomSerializer, DefaultFormats, Formats, JObject, JValue}
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
 * @param formats
 *   Json4s data type formats
 */
final case class Json4sNativeJsonCodec(formats: Formats = Json4sNativeJsonCodec.formats) extends Json4sNativeJsonMeta {

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

  val formats: Formats = DefaultFormats.strict.withBigDecimal + new CustomSerializer[Unit](_ =>
    (
      {
        case JObject(values) if values.isEmpty => ()
      },
      {
        case () => JObject()
      },
    )
  )
}
