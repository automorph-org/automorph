package test.codec

import automorph.spi.MessageCodec
import com.fasterxml.jackson.databind.ObjectMapper
import org.scalacheck.Arbitrary
import test.base.BaseTest

/**
 * JSON message codec test.
 *
 * Checks message serialization.
 */
trait JsonMessageCodecTest extends BaseTest {

  type Value
  type ActualCodec <: MessageCodec[Value]

  def codec: ActualCodec

  implicit def arbitraryNode: Arbitrary[Value]

  private val jsonObjectMapper = new ObjectMapper()

  "" - {
    "JSON" - {
      "Serialize" in {
        forAll { (node: Value) =>
          val serialized = codec.serialize(node)
          jsonObjectMapper.readTree(serialized)
        }
      }
      "Text" in {
        forAll { (node: Value) =>
          val text = codec.text(node)
          jsonObjectMapper.readTree(text)
        }
      }
    }
  }
}
