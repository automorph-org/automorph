package automorph.codec

import com.fasterxml.jackson.annotation.JsonInclude.Include
import com.fasterxml.jackson.core.{JsonGenerator, JsonParseException, JsonParser, TreeNode}
import com.fasterxml.jackson.databind.*
import com.fasterxml.jackson.databind.deser.std.StdDeserializer
import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.databind.node.{NumericNode, ObjectNode}
import com.fasterxml.jackson.databind.ser.std.StdSerializer
import com.fasterxml.jackson.dataformat.cbor.databind.CBORMapper
import com.fasterxml.jackson.dataformat.ion.IonObjectMapper
import com.fasterxml.jackson.dataformat.smile.databind.SmileMapper
import com.fasterxml.jackson.module.scala.{ClassTagExtensions, DefaultScalaModule}
import java.util.Base64
import scala.runtime.BoxedUnit
import scala.util.{Failure, Try}

/**
 * Jackson message codec plugin.
 *
 * Specific message format depends on the supplied object mapper with the following options:
 * - JSON (default) - JacksonCodec.jsonMapper
 * - CBOR - JacksonCodec.cborMapper
 * - Smile - JacksonCodec.smileMapper
 *
 * @see
 *   [[https://www.json.org JSON message format]]
 * @see
 *   [[https://github.com/FasterXML/smile-format-specification Smile message format]]
 * @see
 *   [[https://cbor.io CBOR message format]]
 * @see
 *   [[https://amazon-ion.github.io/ion-docs Ion message format]]
 * @see
 *   [[https://github.com/FasterXML/jackson Library documentation]]
 * @see
 *   [[https://fasterxml.github.io/jackson-databind/javadoc/2.14/com/fasterxml/jackson/databind/JsonNode.html Node type]]
 * @constructor
 *   Creates a Jackson codec plugin using specific message format.
 * @param objectMapper
 *   Jackson object mapper for specific message format
 */
final case class JacksonCodec(objectMapper: ObjectMapper = new JsonMapper) extends JacksonMeta {

  override val mediaType: String = objectMapper match {
    case _: CBORMapper => "application/cbor"
    case _: SmileMapper => "application/x-jackson-smile"
    case _ => "application/json"
  }

  def serialize(node: JsonNode): Array[Byte] =
    objectMapper.writeValueAsBytes(node)

  def deserialize(data: Array[Byte]): JsonNode =
    objectMapper.readTree(data)

  override def text(node: JsonNode): String =
    objectMapper match {
      case _: JsonMapper => objectMapper.writerWithDefaultPrettyPrinter.writeValueAsString(node)
      case _ => Base64.getEncoder.encodeToString(objectMapper.writeValueAsBytes(node))
    }
}

object JacksonCodec {

  /** Message node type. */
  type Node = JsonNode

  private lazy val unitModule: SimpleModule = new SimpleModule().addSerializer(
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

  private lazy val bigDecimalModule: SimpleModule = new SimpleModule().addSerializer(
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

  /** Default Jackson JSON object mapper. */
  def jsonMapper: ObjectMapper =
    JsonMapper.builder()
      .addModule(DefaultScalaModule)
      .addModule(unitModule)
      .addModule(bigDecimalModule)
      .addModule(JacksonJsonRpc.module)
      .addModule(JacksonWebRpc.module)
      .configure(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES, true)
      .configure(DeserializationFeature.FAIL_ON_NULL_CREATOR_PROPERTIES, true)
      .serializationInclusion(Include.NON_ABSENT)
      .defaultLeniency(false)
      .build() :: ClassTagExtensions

  /** Default Jackson Smile object mapper. */
  def smileMapper: ObjectMapper =
    SmileMapper.builder()
      .addModule(DefaultScalaModule)
      .addModule(unitModule)
      .addModule(bigDecimalModule)
      .addModule(JacksonJsonRpc.module)
      .addModule(JacksonWebRpc.module)
      .configure(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES, true)
      .configure(DeserializationFeature.FAIL_ON_NULL_CREATOR_PROPERTIES, true)
      .serializationInclusion(Include.NON_ABSENT)
      .defaultLeniency(false)
      .build() :: ClassTagExtensions

  /** Default Jackson CBOR object mapper. */
  def cborMapper: ObjectMapper =
    CBORMapper.builder()
      .addModule(DefaultScalaModule)
      .addModule(unitModule)
      .addModule(bigDecimalModule)
      .addModule(JacksonJsonRpc.module)
      .addModule(JacksonWebRpc.module)
      .configure(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES, true)
      .configure(DeserializationFeature.FAIL_ON_NULL_CREATOR_PROPERTIES, true)
      .serializationInclusion(Include.NON_ABSENT)
      .defaultLeniency(false)
      .build() :: ClassTagExtensions

  /** Default Jackson Smile object mapper. */
  def ionMapper: ObjectMapper =
    IonObjectMapper.builder()
      .addModule(DefaultScalaModule)
      .addModule(unitModule)
      .addModule(bigDecimalModule)
      .addModule(JacksonJsonRpc.module)
      .addModule(JacksonWebRpc.module)
      .configure(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES, true)
      .configure(DeserializationFeature.FAIL_ON_NULL_CREATOR_PROPERTIES, true)
      .serializationInclusion(Include.NON_ABSENT)
      .defaultLeniency(false)
      .build() :: ClassTagExtensions
}
