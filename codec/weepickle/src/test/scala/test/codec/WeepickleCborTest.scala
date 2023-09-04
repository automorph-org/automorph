package test.codec

import automorph.codec.WeepickleCodec
import com.rallyhealth.weejson.v1.Value
import com.rallyhealth.weepickle.v1.WeePickle.FromTo
import org.scalacheck.Arbitrary
import test.api.Generators.arbitraryRecord
import test.api.Record
import scala.annotation.nowarn

class WeepickleCborTest extends MessageCodecTest {

  type Node = Value
  type ActualCodec = WeepickleCodec

  override lazy val codec: ActualCodec = WeepickleCodec(WeepickleCodec.cborFactory)

  override lazy val arbitraryNode: Arbitrary[Node] = WeepickleTest.arbitraryNode

  @nowarn("msg=used")
  private implicit val recordFromTo: FromTo[Record] = WeepickleTest.recordFromTo

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
