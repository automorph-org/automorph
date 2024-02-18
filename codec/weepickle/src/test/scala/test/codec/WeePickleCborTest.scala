package test.codec

import automorph.codec.WeePickleCodec
import com.fasterxml.jackson.core.JsonFactory

final class WeePickleCborTest extends WeePickleTest {

  override val jsonFactory: JsonFactory = WeePickleCodec.cborFactory
}
