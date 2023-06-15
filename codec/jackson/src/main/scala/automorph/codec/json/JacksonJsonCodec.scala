package automorph.codec.json

import com.fasterxml.jackson.annotation.JsonInclude.Include
import com.fasterxml.jackson.core.{JsonGenerator, JsonParseException, JsonParser, TreeNode}
import com.fasterxml.jackson.databind.deser.std.StdDeserializer
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.databind.node.{NumericNode, ObjectNode}
import com.fasterxml.jackson.databind.ser.std.StdSerializer
import com.fasterxml.jackson.databind.{
  DeserializationContext, DeserializationFeature, JsonNode, ObjectMapper, SerializerProvider,
}
import com.fasterxml.jackson.module.scala.{ClassTagExtensions, DefaultScalaModule}
import scala.runtime.BoxedUnit
import scala.util.{Failure, Try}

/**
 * Jackson message codec plugin.
 *
 * @see
 *   [[https://www.json.org Message format]]
 * @see
 *   [[https://github.com/FasterXML/jackson Library documentation]]
 * @see
 *   [[https://fasterxml.github.io/jackson-databind/javadoc/2.12/com/fasterxml/jackson/databind/JsonNode.html Node type]]
 * @constructor
 *   Creates a Jackson codec plugin using JSON as message format.
 * @param objectMapper
 *   Jackson case object mapper
 */
final case class JacksonJsonCodec(objectMapper: ObjectMapper = JacksonJsonCodec.defaultMapper) extends JacksonJsonMeta {

  override val mediaType: String = "application/json"

  def serialize(node: JsonNode): Array[Byte] =
    objectMapper.writeValueAsBytes(node)

  def deserialize(data: Array[Byte]): JsonNode =
    objectMapper.readTree(data)

  override def text(node: JsonNode): String =
    objectMapper.writerWithDefaultPrettyPrinter.writeValueAsString(node)
}

case object JacksonJsonCodec {

  /** Message node type. */
  type Node = JsonNode

  /** Default Jackson object mapper. */
  lazy val defaultMapper: ObjectMapper = (new ObjectMapper with ClassTagExtensions).registerModule(DefaultScalaModule)
    .registerModule(unitModule).registerModule(bigDecimalModule).registerModule(JacksonJsonRpc.module)
    .registerModule(JacksonWebRpc.module).configure(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES, true)
    .configure(DeserializationFeature.FAIL_ON_NULL_CREATOR_PROPERTIES, true)
    .setSerializationInclusion(Include.NON_ABSENT).setDefaultLeniency(false)

  private lazy val unitModule = new SimpleModule().addSerializer(
    classOf[BoxedUnit],
    new StdSerializer[BoxedUnit](classOf[BoxedUnit]) {

      override def serialize(value: BoxedUnit, generator: JsonGenerator, provider: SerializerProvider): Unit = {
        generator.writeStartObject()
        generator.writeEndObject()
      }

    },
  ).addDeserializer(
    classOf[BoxedUnit],
    new StdDeserializer[BoxedUnit](classOf[BoxedUnit]) {

      override def deserialize(parser: JsonParser, context: DeserializationContext): BoxedUnit =
        parser.readValueAsTree[TreeNode]() match {
          case _: ObjectNode => BoxedUnit.UNIT
          case _ => throw new JsonParseException(parser, "Invalid unit value", parser.getCurrentLocation)
        }
    },
  )

  private lazy val bigDecimalModule = new SimpleModule().addSerializer(
    classOf[BigDecimal],
    new StdSerializer[BigDecimal](classOf[BigDecimal]) {

      override def serialize(value: BigDecimal, generator: JsonGenerator, provider: SerializerProvider): Unit =
        generator.writeNumber(value.bigDecimal)

    },
  ).addDeserializer(
    classOf[BigDecimal],
    new StdDeserializer[BigDecimal](classOf[BigDecimal]) {

      override def deserialize(parser: JsonParser, context: DeserializationContext): BigDecimal =
        parser.readValueAsTree[TreeNode]() match {
          case value: NumericNode => Try(BigDecimal(value.decimalValue)).recoverWith { case error =>
              Failure(new JsonParseException(parser, "Invalid numeric value", parser.getCurrentLocation, error))
            }.get
          case _ => throw new JsonParseException(parser, "Invalid numeric value", parser.getCurrentLocation)
        }
    },
  )
}
