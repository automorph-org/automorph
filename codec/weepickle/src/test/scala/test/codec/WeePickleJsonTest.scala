package test.codec

import automorph.codec.WeePickleCodec
import com.fasterxml.jackson.core.JsonFactory
import test.codec.json.JsonMessageCodecTest

final class WeePickleJsonTest extends WeePickleTest with JsonMessageCodecTest {

  override val jsonFactory: JsonFactory = WeePickleCodec.jsonFactory
}
