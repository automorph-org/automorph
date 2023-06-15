package test.protocol

import automorph.Default
import automorph.protocol.JsonRpcProtocol
import test.base.BaseTest

class JsonRpcTest extends BaseTest {

  "" - {
    "API description" - {
      "OpenRPC" in {
        val protocol = JsonRpcProtocol[Default.Node, Default.Codec, Default.ServerContext](Default.messageCodec)
        val description = protocol.apiSchemas.find(_.function.name == JsonRpcProtocol.openRpcFunction)
        description.shouldNot(be(empty))
      }
      "OpenAPI" in {
        val protocol = JsonRpcProtocol[Default.Node, Default.Codec, Default.ServerContext](Default.messageCodec)
        val description = protocol.apiSchemas.find(_.function.name == JsonRpcProtocol.openApiFunction)
        description.shouldNot(be(empty))
      }
    }
  }
}
