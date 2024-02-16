package test.codec

import automorph.codec.WeepickleCodec
import com.fasterxml.jackson.core.JsonFactory

class WeePickleSmileTest extends WeePickleTest {

  override val jsonFactory: JsonFactory = WeepickleCodec.smileFactory
}
