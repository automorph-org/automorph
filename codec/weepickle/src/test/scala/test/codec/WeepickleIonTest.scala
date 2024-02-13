package test.codec

import automorph.codec.WeepickleCodec
import com.fasterxml.jackson.core.JsonFactory

class WeepickleIonTest extends WeepickleTest {

  override val jsonFactory: JsonFactory = WeepickleCodec.ionFactory
}
