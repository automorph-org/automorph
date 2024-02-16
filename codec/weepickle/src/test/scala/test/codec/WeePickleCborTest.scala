package test.codec

import automorph.codec.WeepickleCodec
import com.fasterxml.jackson.core.JsonFactory

class WeePickleCborTest extends WeePickleTest {

  override val jsonFactory: JsonFactory = WeepickleCodec.cborFactory
}
