package test.codec

import automorph.codec.PlayJsonCodec
import org.scalacheck.{Arbitrary, Gen}
import play.api.libs.json.*
import test.api.Generators.arbitraryRecord
import test.api.{Enum, Record, Structure}

final class PlayJsonTest extends JsonMessageCodecTest {

  type Node = JsValue
  type ActualCodec = PlayJsonCodec

  override lazy val codec: ActualCodec = PlayJsonCodec()

  override lazy val arbitraryNode: Arbitrary[JsValue] = Arbitrary(Gen.recursive[JsValue] { recurse =>
    Gen.oneOf(
      Gen.const(JsNull),
      Gen.resultOf[String, JsValue](JsString.apply),
      Gen.resultOf[Double, JsValue](value => JsNumber(BigDecimal(value))),
      Gen.resultOf[Boolean, JsValue](JsBoolean.apply),
      Gen.listOfN[JsValue](2, recurse).map(JsArray.apply),
      Gen.mapOfN(2, Gen.zip(Arbitrary.arbitrary[String], recurse)).map(JsObject.apply),
    )
  })

  @scala.annotation.nowarn("msg=never used")
  implicit private val recordReads: Reads[Record] = {
    implicit val enumReads: Reads[Enum.Enum] = (json: JsValue) => json.validate[Int].map(Enum.fromOrdinal)
    implicit val structureReads: Reads[Structure] = Json.reads
    Json.reads
  }
  @scala.annotation.nowarn("msg=never used")
  implicit private val recordWrites: Writes[Record] = {
    implicit val enumWrites: Writes[Enum.Enum] = (o: Enum.Enum) => JsNumber(BigDecimal(Enum.toOrdinal(o)))
    implicit val structureWrites: Writes[Structure] = Json.writes
    Json.writes
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
