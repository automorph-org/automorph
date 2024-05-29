package test.codec

import automorph.codec.UPickleMessagePackCodec
import automorph.codec.UPickleMessagePackCodec.MessagePackConfig
import org.scalacheck.{Arbitrary, Gen}
import test.api.Generators.arbitraryRecord
import test.api.{Enum, Record, Structure}
import upack.{Arr, Bool, Float64, Msg, Null, Obj, Str}
import upickle.core.LinkedHashMap

final class UpickleMessagePackTest extends MessageCodecTest {

  type Value = Msg
  type ActualCodec = UPickleMessagePackCodec[UpickleMessagePackTest.type]

  override lazy val codec: ActualCodec = UPickleMessagePackCodec(UpickleMessagePackTest)

  override lazy val arbitraryValue: Arbitrary[Value] = Arbitrary(Gen.recursive[Value] { recurse =>
    Gen.oneOf(
      Gen.const(Null),
      Gen.resultOf[String, Value](Str.apply),
      Gen.resultOf[Double, Value](Float64.apply),
      Gen.resultOf[Boolean, Value](Bool.apply),
      Gen.listOfN[Value](2, recurse).map(Arr(_*)),
      Gen.mapOfN(2, Gen.zip(Gen.resultOf[String, Msg](Str.apply), recurse)).map { values =>
        Obj(LinkedHashMap(values))
      },
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

object UpickleMessagePackTest extends MessagePackConfig {

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
