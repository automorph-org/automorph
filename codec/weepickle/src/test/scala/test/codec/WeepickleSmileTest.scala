package test.codec

import automorph.codec.WeepickleCodec
import com.rallyhealth.weejson.v1.Value
import com.rallyhealth.weepickle.v1.WeePickle.FromTo
import org.scalacheck.Arbitrary
import test.api.Generators.arbitraryRecord
import test.api.Record
import scala.annotation.nowarn

class WeepickleSmileTest extends MessageCodecTest {

  type Node = Value
  type ActualCodec = WeepickleCodec

  override lazy val codec: ActualCodec = WeepickleCodec(WeepickleCodec.smileFactory)

  override lazy val arbitraryNode: Arbitrary[Node] = WeepickleTest.arbitraryNode

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
