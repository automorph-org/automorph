package test.codec.messagepack

import automorph.codec.messagepack.{UPickleMessagePackCodec, UPickleMessagePackConfig}
import org.scalacheck.{Arbitrary, Gen}
import test.api.Generators.arbitraryRecord
import test.api.{Enum, Record, Structure}
import test.codec.MessageCodecTest
import upack.{Arr, Bool, Float64, Msg, Null, Obj, Str}
import upickle.core.LinkedHashMap

class UpickleMessagePackTest extends MessageCodecTest {

  type Node = Msg
  type ActualCodec = UPickleMessagePackCodec[UpickleMessagePackTest.type]

  override lazy val codec: ActualCodec = UPickleMessagePackCodec(UpickleMessagePackTest)

  override lazy val arbitraryNode: Arbitrary[Node] = Arbitrary(Gen.recursive[Node] { recurse =>
    Gen.oneOf(
      Gen.const(Null),
      Gen.resultOf[String, Node](Str.apply),
      Gen.resultOf[Double, Node](Float64.apply),
      Gen.resultOf[Boolean, Node](Bool.apply),
      Gen.listOfN[Node](2, recurse).map(Arr(_ *)),
      Gen.mapOfN(2, Gen.zip(Gen.resultOf[String, Msg](Str.apply), recurse)).map { values =>
        Obj(LinkedHashMap(values))
      }
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

object UpickleMessagePackTest extends UPickleMessagePackConfig {

  @scala.annotation.nowarn("msg=never used")
  implicit lazy val recordRw: ReadWriter[Record] = {
    implicit val enumRw: ReadWriter[Enum.Enum] = readwriter[Int].bimap[Enum.Enum](
      value => Enum.toOrdinal(value),
      number => Enum.fromOrdinal(number)
    )
    implicit val structureRw: ReadWriter[Structure] = macroRW
    macroRW
  }
}
