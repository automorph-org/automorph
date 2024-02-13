package test.codec.json

import automorph.codec.json.Json4sNativeJsonCodec
import org.json4s.{CustomSerializer, DefaultFormats, Formats, JArray, JBool, JDouble, JInt, JNull, JObject, JString, JValue}
import org.scalacheck.{Arbitrary, Gen}
import test.api.Generators.arbitraryRecord
import test.api.{Enum, Record}

class Json4sNativeJsonTest extends JsonMessageCodecTest {

  type Node = JValue
  type ActualCodec = Json4sNativeJsonCodec

  override lazy val codec: ActualCodec = Json4sNativeJsonCodec()

  override lazy val arbitraryNode: Arbitrary[Node] = Arbitrary(Gen.recursive[Node] { recurse =>
    Gen.oneOf(
      Gen.const(JNull),
      Gen.resultOf(JString.apply),
      Gen.resultOf(JDouble.apply),
      Gen.resultOf(JBool.apply),
      Gen.listOfN[Node](2, Gen.oneOf(Gen.const(JNull), recurse)).map(JArray.apply),
      Gen.mapOfN(2, Gen.zip(Arbitrary.arbitrary[String], recurse)).map(entries => JObject(entries.toSeq *)),
    )
  })

  private val enumSerializer = new CustomSerializer[Enum.Enum](_ => (
    {
      case JInt(value) => Enum.fromOrdinal(value.toInt)
    }, {
      case value: Enum.Enum => JInt(Enum.toOrdinal(value))
    }
  ))

  implicit val formats: Formats = DefaultFormats + enumSerializer

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
