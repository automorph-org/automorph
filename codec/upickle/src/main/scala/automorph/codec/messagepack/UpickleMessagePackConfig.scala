package automorph.codec.messagepack

import automorph.codec.UpickleConfig
import automorph.schema.{OpenApi, OpenRpc}

/** uPickle reader and writer instances providing basic null-safe data types support for MessagePack format. */
trait UpickleMessagePackConfig extends UpickleConfig {

  implicit lazy val jsonRpcMessageRw: ReadWriter[UpickleJsonRpc.RpcMessage] = UpickleJsonRpc.readWriter(this)
  implicit lazy val restRpcMessageRw: ReadWriter[UpickleWebRpc.RpcMessage] = UpickleWebRpc.readWriter(this)
  implicit lazy val openRpcRw: ReadWriter[OpenRpc] = UpickleOpenRpc.readWriter(this)
  implicit lazy val openApiRw: ReadWriter[OpenApi] = UpickleOpenApi.readWriter(this)
}

object UpickleMessagePackConfig {

  /** Default data types support for uPickle message codec using MessagePack format. */
  lazy val default: UpickleMessagePackConfig = new UpickleMessagePackConfig {}
}
