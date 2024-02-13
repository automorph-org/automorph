package test.codec

import automorph.codec.WeepickleCodec
import com.fasterxml.jackson.core.JsonFactory

class WeepickleSmileTest extends WeepickleTest {

  override val jsonFactory: JsonFactory = WeepickleCodec.smileFactory
}
