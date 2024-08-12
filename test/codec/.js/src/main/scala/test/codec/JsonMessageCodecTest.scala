package test.codec

import automorph.spi.MessageCodec
import org.scalacheck.Arbitrary
import test.base.BaseTest
import scalajs.js.JSON

/**
 * JSON message codec test.
 *
 * Checks message serialization.
 */
trait JsonMessageCodecTest extends BaseTest {

  type Value
  type ActualCodec <: MessageCodec[Value]

  def codec: ActualCodec

  implicit def arbitraryValue: Arbitrary[Value]

  "" - {
    "JSON" - {
      "Serialize" in {
        forAll { (node: Value) =>
          val serialized = codec.serialize(node)
          JSON.parse(new String(serialized))
        }
      }
      "Text" in {
        forAll { (node: Value) =>
          val text = codec.text(node)
          JSON.parse(text)
        }
      }
    }
  }
}
