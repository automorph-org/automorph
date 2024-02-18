package automorph.codec.json

import automorph.codec.UPickleConfig
import automorph.schema.{OpenApi, OpenRpc}

/** uPickle reader and writer instances providing basic null-safe data types support for JSON format. */
trait UPickleJsonConfig extends UPickleConfig {

  implicit lazy val jsonRpcMessageRw: ReadWriter[UPickleJsonRpc.RpcMessage] = UPickleJsonRpc.readWriter(this)
  implicit lazy val restRpcMessageRw: ReadWriter[UPickleWebRpc.RpcMessage] = UPickleWebRpc.readWriter(this)
  implicit lazy val openRpcRw: ReadWriter[OpenRpc] = UPickleOpenRpc.readWriter(this)
  implicit lazy val openApiRw: ReadWriter[OpenApi] = UPickleOpenApi.readWriter(this)
}

object UPickleJsonConfig {

  /** Default data types support for uPickle message codec using JSON format. */
  lazy val default: UPickleJsonConfig = new UPickleJsonConfig {}
}
