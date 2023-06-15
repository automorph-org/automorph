package test.codec.json

import io.circe.generic.auto.*
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.{Decoder, Encoder, Json}
import automorph.codec.json.CirceJsonCodec
import org.scalacheck.{Arbitrary, Gen}
import test.api.Generators.arbitraryRecord
import test.api.{Enum, Record, Structure}

class CirceJsonTest extends JsonMessageCodecTest {

  type Node = Json
  type ActualCodec = CirceJsonCodec

  override lazy val codec: ActualCodec = CirceJsonCodec()

  override lazy val arbitraryNode: Arbitrary[Node] = Arbitrary(
    Gen.oneOf(
      Gen.const(Json.Null),
      Gen.recursive[Node] { recurse =>
        Gen.oneOf(
          // FIXME - restore after null object value serialization bug in Circe for Scala 3 is fixed
          // Gen.const(Json.Null),
          Gen.resultOf(Json.fromString _),
          Gen.resultOf(Json.fromDoubleOrString _),
          Gen.resultOf(Json.fromBoolean _),
          Gen.listOfN[Node](2, Gen.oneOf(Gen.const(Json.Null), recurse)).map(Json.fromValues),
          Gen.mapOfN(2, Gen.zip(Arbitrary.arbitrary[String], recurse)).map(Json.fromFields),
        )
      }
    )
  )

  private implicit lazy val enumEncoder: Encoder[Enum.Enum] = Encoder.encodeInt.contramap[Enum.Enum](Enum.toOrdinal)
  private implicit lazy val enumDecoder: Decoder[Enum.Enum] = Decoder.decodeInt.map(Enum.fromOrdinal)
  private implicit lazy val structureEncoder: Encoder[Structure] = deriveEncoder[Structure]
  private implicit lazy val structureDecoder: Decoder[Structure] = deriveDecoder[Structure]

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
