package test.codec

import automorph.codec.WeepickleCodec
import com.fasterxml.jackson.core.JsonFactory
import com.rallyhealth.weejson.v1.{Arr, Bool, Null, Num, Obj, Str, Value}
import com.rallyhealth.weepickle.v1.WeePickle.{FromInt, FromTo, ToInt, macroFromTo}
import org.scalacheck.{Arbitrary, Gen}
import test.api.{Enum, Record, Structure}
import test.api.Generators.arbitraryRecord

trait WeePickleTest extends MessageCodecTest {

  type Node = Value
  type ActualCodec = WeepickleCodec

  override lazy val codec: ActualCodec = WeepickleCodec(jsonFactory)

  override lazy val arbitraryNode: Arbitrary[Value] = Arbitrary(Gen.recursive[Value] { recurse =>
    Gen.oneOf(
      Gen.const(Null),
      Gen.resultOf[String, Value](Str.apply),
      Gen.resultOf[Double, Value](value => Num(BigDecimal(value))),
      Gen.resultOf[Boolean, Value](Bool.apply),
      Gen.listOfN[Value](2, recurse).map(Arr(_ *)),
      Gen.mapOfN(2, Gen.zip(Arbitrary.arbitrary[String], recurse)).map(Obj.from)
    )
  })

  def jsonFactory: JsonFactory

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
