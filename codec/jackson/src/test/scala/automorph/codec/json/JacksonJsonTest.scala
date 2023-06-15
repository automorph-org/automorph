package automorph.codec.json

import com.fasterxml.jackson.core.{JsonGenerator, JsonParser}
import com.fasterxml.jackson.databind.deser.std.StdDeserializer
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.databind.node.{ArrayNode, BooleanNode, DoubleNode, NullNode, ObjectNode, TextNode}
import com.fasterxml.jackson.databind.ser.std.StdSerializer
import com.fasterxml.jackson.databind.{DeserializationContext, JsonNode, SerializerProvider}
import org.scalacheck.{Arbitrary, Gen}
import scala.jdk.CollectionConverters.{MapHasAsJava, SeqHasAsJava}
import test.api.Generators.arbitraryRecord
import test.codec.json.JsonMessageCodecTest
import test.api.{Enum, Record}

class JacksonJsonTest extends JsonMessageCodecTest {

  type Node = JsonNode
  type ActualCodec = JacksonJsonCodec
  override lazy val codec: ActualCodec = JacksonJsonCodec(JacksonJsonCodec.defaultMapper.registerModule(enumModule))

  override lazy val arbitraryNode: Arbitrary[Node] = Arbitrary(Gen.recursive[Node] { recurse =>
    Gen.oneOf(
      Gen.const(NullNode.instance),
      Gen.resultOf(TextNode.valueOf _),
      Gen.resultOf(DoubleNode.valueOf _),
      Gen.resultOf(BooleanNode.valueOf _),
      Gen.listOfN[Node](2, recurse).map {
        (values: List[Node]) => new ArrayNode(JacksonJsonCodec.defaultMapper.getNodeFactory, values.asJava)
      },
      Gen.mapOfN(2, Gen.zip(Arbitrary.arbitrary[String], recurse)).map {
        values => new ObjectNode(JacksonJsonCodec.defaultMapper.getNodeFactory, values.asJava)
      },
    )
  })

  private lazy val enumModule = new SimpleModule().addSerializer(
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
