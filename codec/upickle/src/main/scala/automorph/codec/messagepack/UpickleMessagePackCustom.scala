package automorph.codec.messagepack

import automorph.codec.UpickleCustom
import automorph.schema.{OpenApi, OpenRpc}

/**
 * uPickle message codec customization for MessagePack format.
 *
 * Provides basic null-safe data types and RPC protocol message support.
 */
trait UpickleMessagePackCustom extends UpickleCustom {

  implicit lazy val jsonRpcMessageRw: ReadWriter[UpickleJsonRpc.RpcMessage] = UpickleJsonRpc.readWriter(this)

  implicit lazy val restRpcMessageRw: ReadWriter[UpickleWebRpc.RpcMessage] = UpickleWebRpc.readWriter(this)

  implicit lazy val openRpcRw: ReadWriter[OpenRpc] = UpickleOpenRpc.readWriter(this)
  implicit lazy val openApiRw: ReadWriter[OpenApi] = UpickleOpenApi.readWriter(this)
}

case object UpickleMessagePackCustom {

  /** Default data types support for uPickle message codec using MessagePack format. */
  lazy val default: UpickleMessagePackCustom = new UpickleMessagePackCustom {}
}
