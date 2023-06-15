package test.codec

import automorph.spi.MessageCodec
import java.nio.charset.StandardCharsets
import org.scalacheck.Arbitrary
import test.base.BaseTest

/**
 * Message codec test.
 *
 * Checks message serialization, deserialization and textual representation.
 */
trait MessageCodecTest extends BaseTest {

  type Node
  type ActualCodec <: MessageCodec[Node]

  def codec: ActualCodec

  implicit def arbitraryNode: Arbitrary[Node]

  private val charset = StandardCharsets.UTF_8

  "" - {
    "Serialize & Deserialize" in {
      forAll { (node: Node) =>
        val serialized = codec.serialize(node)
        val deserialized = codec.deserialize(serialized)
        deserialized.shouldEqual(node)
      }
    }
    "Text" in {
      forAll { (node: Node) =>
        val textBinaryLength = codec.text(node).getBytes(charset).length
        val serializedBinaryLengthAs = codec.serialize(node).length
        textBinaryLength.shouldBe(>=(serializedBinaryLengthAs))
      }
    }
  }
}
