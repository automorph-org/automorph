package automorph.codec.json

import com.fasterxml.jackson.core.{JsonGenerator, JsonParseException, JsonParser}
import com.fasterxml.jackson.databind.{JsonNode, SerializerProvider}
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.databind.ser.std.StdSerializer

object JacksonRpcProtocol {

  private[automorph] def serializer[T <: Product](serializedClass: Class[T]) =
    new StdSerializer[T](serializedClass) {

      override def serialize(value: T, generator: JsonGenerator, provider: SerializerProvider): Unit = {
        val entries = value.productElementNames.zip(value.productIterator).flatMap {
          case (name, Some(value)) => Some(name -> value)
          case (_, None) => None
        }.toMap
        generator.writeObject(entries)
      }
    }

  private[automorph] def field[T](
    name: String,
    extract: JsonNode => Option[T],
    node: ObjectNode,
    parser: JsonParser
  ): Option[T] =
    Option(node.get(name)).filter(!_.isNull).map(extract).map(_.getOrElse {
      throw new JsonParseException(parser, s"Invalid $name", parser.currentLocation)
    })
}
