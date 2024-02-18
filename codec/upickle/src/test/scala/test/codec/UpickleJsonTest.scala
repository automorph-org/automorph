package test.codec

import automorph.codec.UPickleJsonCodec
import automorph.codec.UPickleJsonCodec.JsonConfig
import org.scalacheck.{Arbitrary, Gen}
import test.api.Generators.arbitraryRecord
import test.api.{Enum, Record, Structure}
import ujson.{Arr, Bool, Null, Num, Obj, Str, Value}

final class UpickleJsonTest extends MessageCodecTest with JsonMessageCodecTest {

  type Node = Value
  type ActualCodec = UPickleJsonCodec[UpickleJsonTest.type]

  override lazy val codec: ActualCodec = UPickleJsonCodec(UpickleJsonTest)

  override lazy val arbitraryNode: Arbitrary[Node] = Arbitrary(Gen.recursive[Node] { recurse =>
    Gen.oneOf(
      Gen.const(Null),
      Gen.resultOf[String, Node](Str.apply),
      Gen.resultOf[Double, Node](Num.apply),
      Gen.resultOf[Boolean, Node](Bool.apply),
      Gen.listOfN[Node](2, recurse).map(Arr(_*)),
      Gen.mapOfN(2, Gen.zip(Arbitrary.arbitrary[String], recurse)).map(Obj.from),
    )
  })

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

object UpickleJsonTest extends JsonConfig {

  @scala.annotation.nowarn("msg=never used")
  implicit lazy val recordRw: ReadWriter[Record] = {
    implicit val enumRw: ReadWriter[Enum.Enum] = readwriter[Int].bimap[Enum.Enum](
      value => Enum.toOrdinal(value),
      number => Enum.fromOrdinal(number),
    )
    implicit val structureRw: ReadWriter[Structure] = macroRW
    macroRW
  }
}
