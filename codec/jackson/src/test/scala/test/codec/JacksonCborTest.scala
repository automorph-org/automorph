package test.codec

import automorph.codec.JacksonCodec
import com.fasterxml.jackson.databind.ObjectMapper

final class JacksonCborTest extends JacksonTest {

  override val objectMapper: ObjectMapper = JacksonCodec.cborMapper.registerModule(enumModule)
}
