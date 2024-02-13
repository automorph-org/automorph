package test.codec

import automorph.codec.JacksonCodec
import com.fasterxml.jackson.core.{JsonGenerator, JsonParser}
import com.fasterxml.jackson.databind.deser.std.StdDeserializer
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.databind.node.*
import com.fasterxml.jackson.databind.ser.std.StdSerializer
import com.fasterxml.jackson.databind.{DeserializationContext, JsonNode, ObjectMapper, SerializerProvider}
import org.scalacheck.{Arbitrary, Gen}
import test.api.{Enum, Record}
import test.api.Generators.arbitraryRecord
import scala.jdk.CollectionConverters.{MapHasAsJava, SeqHasAsJava}

trait JacksonTest extends MessageCodecTest {

  type Node = JsonNode
  type ActualCodec = JacksonCodec

  override lazy val codec: ActualCodec = JacksonCodec(objectMapper)

  override lazy val arbitraryNode: Arbitrary[JsonNode] = {
    val nodeFactory = JacksonCodec.jsonMapper.getNodeFactory
    Arbitrary(Gen.recursive[JsonNode] { recurse =>
      Gen.oneOf(
        Gen.const(NullNode.instance),
        Gen.resultOf(TextNode.valueOf _),
        Gen.resultOf(DoubleNode.valueOf _),
        Gen.resultOf(BooleanNode.valueOf _),
        Gen.listOfN[JsonNode](2, recurse).map(values => new ArrayNode(nodeFactory, values.asJava)),
        Gen.mapOfN(2, Gen.zip(Arbitrary.arbitrary[String], recurse)).map {
          values => new ObjectNode(nodeFactory, values.asJava)
        },
      )
    })
  }

  def objectMapper: ObjectMapper

  val enumModule: SimpleModule = new SimpleModule().addSerializer(
    classOf[Enum.Enum],
    new StdSerializer[Enum.Enum](classOf[Enum.Enum]) {

      override def serialize(value: Enum.Enum, generator: JsonGenerator, provider: SerializerProvider): Unit =
        generator.writeNumber(Enum.toOrdinal(value))
    },
  ).addDeserializer(
    classOf[Enum.Enum],
    new StdDeserializer[Enum.Enum](classOf[Enum.Enum]) {

      override def deserialize(parser: JsonParser, context: DeserializationContext): Enum.Enum =
        Enum.fromOrdinal(parser.getIntValue)
    },
  )

  "" - {
    "Encode & Decode" in {
      forAll { (record: Record) =>
        val encoded = codec.encode(record)
        val decoded = codec.decode[Record](encoded)
        decoded.shouldEqual(record)
      }
    }
  }
}
