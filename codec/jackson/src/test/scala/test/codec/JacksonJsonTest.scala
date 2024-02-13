package test.codec

import automorph.codec.JacksonCodec
import com.fasterxml.jackson.databind.ObjectMapper
import test.codec.json.JsonMessageCodecTest

class JacksonJsonTest extends JacksonTest with JsonMessageCodecTest {

  override val objectMapper: ObjectMapper = JacksonCodec.jsonMapper.registerModule(enumModule)
}
