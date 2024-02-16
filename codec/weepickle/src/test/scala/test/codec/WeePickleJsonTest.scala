package test.codec

import automorph.codec.WeepickleCodec
import com.fasterxml.jackson.core.JsonFactory
import test.codec.json.JsonMessageCodecTest

class WeePickleJsonTest extends WeePickleTest with JsonMessageCodecTest {

  override val jsonFactory: JsonFactory = WeepickleCodec.jsonFactory
}
