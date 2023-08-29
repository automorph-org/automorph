package automorph.codec

import com.fasterxml.jackson.databind.JsonNode
import org.scalacheck.Arbitrary
import test.api.Generators.arbitraryRecord
import test.api.Record
import test.codec.MessageCodecTest

class JacksonSmileTest extends MessageCodecTest {

  type Node = JsonNode
  type ActualCodec = JacksonCodec

  override lazy val arbitraryNode: Arbitrary[Node] = JacksonTest.arbitraryNode

  override lazy val codec: ActualCodec = JacksonCodec(JacksonCodec.smileMapper.registerModule(JacksonTest.enumModule))

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
