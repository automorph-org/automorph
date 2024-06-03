package automorph.codec

import com.fasterxml.jackson.core.{JsonGenerator, JsonParseException, JsonParser}
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.databind.ser.std.StdSerializer
import com.fasterxml.jackson.databind.{JsonNode, SerializerProvider}

private[automorph] object JacksonRpcProtocol {

  def serializer[T <: Product](serializedClass: Class[T]): StdSerializer[T] =
    new StdSerializer[T](serializedClass) {

      override def serialize(value: T, generator: JsonGenerator, provider: SerializerProvider): Unit = {
        val entries = value.productElementNames.zip(value.productIterator).flatMap {
          case (name, Some(value)) => Some(name -> value)
          case _ => None
        }.toMap
        generator.writeObject(entries)
      }
    }

  def field[T](
    name: String,
    extract: JsonNode => Option[T],
    node: ObjectNode,
    parser: JsonParser,
  ): Option[T] =
    Option(node.get(name)).filter(!_.isNull).map(extract).map(_.getOrElse {
      throw new JsonParseException(parser, s"Invalid $name", parser.currentLocation)
    })
}
