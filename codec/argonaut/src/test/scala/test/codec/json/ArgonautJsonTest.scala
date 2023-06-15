package test.codec.json

import argonaut.Argonaut.{jArray, jBool, jNull, jNumber, jObjectAssocList, jString}
import argonaut.Json.jNumberOrNull
import argonaut.{Argonaut, CodecJson, Json}
import automorph.codec.json.ArgonautJsonCodec
import org.scalacheck.{Arbitrary, Gen}
import test.api.Generators.arbitraryRecord
import test.api.{Enum, Record, Structure}

class ArgonautJsonTest extends JsonMessageCodecTest {

  type Node = Json
  type ActualCodec = ArgonautJsonCodec

  override lazy val codec: ActualCodec = ArgonautJsonCodec()

  override lazy val arbitraryNode: Arbitrary[Node] = Arbitrary(Gen.recursive[Node] { recurse =>
    Gen.oneOf(
      Gen.const(jNull),
      Gen.resultOf(jString),
      Gen.resultOf[Double, Node](jNumberOrNull),
      Gen.resultOf(jBool),
      Gen.listOfN[Node](2, recurse).map(jArray),
      Gen.mapOfN(2, Gen.zip(Arbitrary.arbitrary[String], recurse)).map { values =>
        jObjectAssocList(values.toList)
      },
    )
  })

  private implicit lazy val enumCodecJson: CodecJson[Enum.Enum] =
    CodecJson((v: Enum.Enum) => jNumber(Enum.toOrdinal(v)), cursor => cursor.focus.as[Int].map(Enum.fromOrdinal))

  private implicit lazy val structureCodecJson: CodecJson[Structure] = Argonaut
    .codec1(Structure.apply, (v: Structure) => v.value)("value")

  private implicit lazy val recordCodecJson: CodecJson[Record] = Argonaut.codec13(
    Record.apply,
    (v: Record) =>
      (
        v.string,
        v.boolean,
        v.byte,
        v.short,
        v.int,
        v.long,
        v.float,
        v.double,
        v.enumeration,
        v.list,
        v.map,
        v.structure,
        v.none,
      ),
  )(
    "string",
    "boolean",
    "byte",
    "short",
    "int",
    "long",
    "float",
    "double",
    "enumeration",
    "list",
    "map",
    "structure",
    "none",
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
