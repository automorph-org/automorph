package test.codec

import automorph.codec.WeePickleCodec
import com.fasterxml.jackson.core.JsonFactory

class WeePickleIonTest extends WeePickleTest {

  override val jsonFactory: JsonFactory = WeePickleCodec.ionFactory
}
