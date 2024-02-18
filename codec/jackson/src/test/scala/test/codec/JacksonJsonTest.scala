package test.codec

import automorph.codec.JacksonCodec
import com.fasterxml.jackson.databind.ObjectMapper

final class JacksonJsonTest extends JacksonTest with JsonMessageCodecTest {

  override val objectMapper: ObjectMapper = JacksonCodec.jsonMapper.registerModule(enumModule)
}
