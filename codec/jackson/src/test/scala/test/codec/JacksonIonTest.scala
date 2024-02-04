package test.codec

import automorph.codec.JacksonCodec
import com.fasterxml.jackson.databind.JsonNode
import org.scalacheck.Arbitrary
import test.api.Generators.arbitraryRecord
import test.api.Record

class JacksonIonTest extends MessageCodecTest {

  type Node = JsonNode
  type ActualCodec = JacksonCodec

  override lazy val codec: ActualCodec = JacksonCodec(JacksonCodec.ionMapper.registerModule(JacksonTest.enumModule))

  override lazy val arbitraryNode: Arbitrary[Node] = JacksonTest.arbitraryNode

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
