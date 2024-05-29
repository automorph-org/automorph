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

  type Value
  type ActualCodec <: MessageCodec[Value]

  def codec: ActualCodec

  implicit def arbitraryValue: Arbitrary[Value]

  private val charset = StandardCharsets.UTF_8

  "" - {
    "Serialize & Deserialize" in {
      forAll { (node: Value) =>
        val serialized = codec.serialize(node)
        val deserialized = codec.deserialize(serialized)
        deserialized.shouldEqual(node)
      }
    }
    "Text" in {
      forAll { (node: Value) =>
        val textLength = codec.text(node).getBytes(charset).length
        val serializedLength = codec.serialize(node).length
        textLength.shouldBe(>=(serializedLength - 1))
      }
    }
  }
}
