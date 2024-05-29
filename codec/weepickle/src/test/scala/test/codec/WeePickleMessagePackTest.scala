package test.codec

import automorph.codec.WeePickleMessagePackCodec
import com.rallyhealth.weepack.v1.{Arr, Bool, Float64, Msg, Null, Obj, Str}
import com.rallyhealth.weepickle.v1.WeePickle.{FromInt, FromTo, ToInt, macroFromTo}
import org.scalacheck.{Arbitrary, Gen}
import test.api.Generators.arbitraryRecord
import test.api.{Enum, Record, Structure}
import scala.collection.mutable

trait WeePickleMessagePackTest extends MessageCodecTest {

  type Value = Msg
  type ActualCodec = WeePickleMessagePackCodec

  override lazy val codec: ActualCodec = WeePickleMessagePackCodec()

  override lazy val arbitraryNode: Arbitrary[Msg] = Arbitrary(Gen.recursive[Msg] { recurse =>
    Gen.oneOf(
      Gen.const(Null),
      Gen.resultOf[String, Value](Str.apply),
      Gen.resultOf[Double, Value](Float64.apply),
      Gen.resultOf[Boolean, Value](Bool.apply),
      Gen.listOfN[Value](2, recurse).map(Arr(_*)),
      Gen.mapOfN(2, Gen.zip(Gen.resultOf[String, Msg](Str.apply), recurse)).map { values =>
        Obj(mutable.LinkedHashMap.newBuilder.addAll(values).result())
      },
    )
  })

  @scala.annotation.nowarn("msg=never used")
  private implicit val recordFromTo: FromTo[Record] = {
    implicit val enumFromTo: FromTo[Enum.Enum] = FromTo.join(ToInt, FromInt).bimap(Enum.toOrdinal, Enum.fromOrdinal)
    implicit val structureFromTo: FromTo[Structure] = macroFromTo
    macroFromTo
  }

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
