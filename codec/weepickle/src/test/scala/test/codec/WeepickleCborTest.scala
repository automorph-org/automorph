package test.codec

import automorph.codec.WeepickleCodec
import com.fasterxml.jackson.core.JsonFactory

class WeepickleCborTest extends WeepickleTest {

  override val jsonFactory: JsonFactory = WeepickleCodec.cborFactory
}
