package test.codec

import automorph.codec.WeePickleCodec
import com.fasterxml.jackson.core.JsonFactory

class WeePickleSmileTest extends WeePickleTest {

  override val jsonFactory: JsonFactory = WeePickleCodec.smileFactory
}
