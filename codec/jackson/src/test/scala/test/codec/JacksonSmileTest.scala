package test.codec

import automorph.codec.JacksonCodec
import com.fasterxml.jackson.databind.ObjectMapper

class JacksonSmileTest extends JacksonTest {

  override val objectMapper: ObjectMapper = JacksonCodec.smileMapper.registerModule(enumModule)
}
