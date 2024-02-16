package automorph.codec.messagepack

import automorph.codec.UPickleConfig
import automorph.schema.{OpenApi, OpenRpc}

/** uPickle reader and writer instances providing basic null-safe data types support for MessagePack format. */
trait UpickleMessagePackConfig extends UPickleConfig {

  implicit lazy val jsonRpcMessageRw: ReadWriter[UPickleJsonRpc.RpcMessage] = UPickleJsonRpc.readWriter(this)
  implicit lazy val restRpcMessageRw: ReadWriter[UPickleWebRpc.RpcMessage] = UPickleWebRpc.readWriter(this)
  implicit lazy val openRpcRw: ReadWriter[OpenRpc] = UPickleOpenRpc.readWriter(this)
  implicit lazy val openApiRw: ReadWriter[OpenApi] = UPickleOpenApi.readWriter(this)
}

object UpickleMessagePackConfig {

  /** Default data types support for uPickle message codec using MessagePack format. */
  lazy val default: UpickleMessagePackConfig = new UpickleMessagePackConfig {}
}
