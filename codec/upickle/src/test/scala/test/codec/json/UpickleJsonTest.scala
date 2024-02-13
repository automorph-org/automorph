package test.codec.json

import automorph.codec.json.{UpickleJsonCodec, UpickleJsonConfig}
import org.scalacheck.{Arbitrary, Gen}
import test.api.Generators.arbitraryRecord
import test.api.{Enum, Record, Structure}
import test.codec.MessageCodecTest
import ujson.{Arr, Bool, Null, Num, Obj, Str, Value}

class UpickleJsonTest extends MessageCodecTest with JsonMessageCodecTest {

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

  private lazy val config = codec.config
  private implicit lazy val recordRw: config.ReadWriter[Record] = config.macroRW

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

object UpickleJsonTest extends UpickleJsonConfig {

  implicit lazy val enumRw: ReadWriter[Enum.Enum] = readwriter[Int].bimap[Enum.Enum](
    value => Enum.toOrdinal(value),
    number => Enum.fromOrdinal(number)
  )
  implicit lazy val structureRw: ReadWriter[Structure] = macroRW
}
