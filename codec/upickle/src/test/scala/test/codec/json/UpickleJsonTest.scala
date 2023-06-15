package test.codec.json

import automorph.codec.json.{UpickleJsonCodec, UpickleJsonCustom}
import org.scalacheck.{Arbitrary, Gen}
import test.api.Generators.arbitraryRecord
import test.api.{Enum, Record, Structure}
import ujson.{Arr, Bool, Null, Num, Obj, Str, Value}

class UpickleJsonTest extends JsonMessageCodecTest {

  type Node = Value
  type ActualCodec = UpickleJsonCodec[UpickleJsonTest.type]

  override lazy val codec: ActualCodec = UpickleJsonCodec(UpickleJsonTest)

  override lazy val arbitraryNode: Arbitrary[Node] = Arbitrary(Gen.recursive[Node] { recurse =>
    Gen.oneOf(
      Gen.const(Null),
      Gen.resultOf[String, Node](Str.apply),
      Gen.resultOf[Double, Node](Num.apply),
      Gen.resultOf[Boolean, Node](Bool.apply),
      Gen.listOfN[Node](2, recurse).map(Arr(_ *)),
      Gen.mapOfN(2, Gen.zip(Arbitrary.arbitrary[String], recurse)).map(Obj.from)
    )
  })

  private lazy val custom = codec.custom
  private implicit lazy val recordRw: custom.ReadWriter[Record] = custom.macroRW

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

case object UpickleJsonTest extends UpickleJsonCustom {

  implicit lazy val enumRw: ReadWriter[Enum.Enum] = readwriter[Int].bimap[Enum.Enum](
    value => Enum.toOrdinal(value),
    number => Enum.fromOrdinal(number)
  )
  implicit lazy val structureRw: ReadWriter[Structure] = macroRW
}
