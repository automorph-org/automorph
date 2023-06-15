package automorph.codec.json

import automorph.codec.UpickleCustom
import automorph.schema.{OpenApi, OpenRpc}

/**
 * uPickle message codec customization for JSON format.
 *
 * Provides basic null-safe data types and RPC protocol message support.
 */
trait UpickleJsonCustom extends UpickleCustom {

  implicit lazy val jsonRpcMessageRw: ReadWriter[UpickleJsonRpc.RpcMessage] = UpickleJsonRpc.readWriter(this)

  implicit lazy val restRpcMessageRw: ReadWriter[UpickleWebRpc.RpcMessage] = UpickleWebRpc.readWriter(this)

  implicit lazy val openRpcRw: ReadWriter[OpenRpc] = UpickleOpenRpc.readWriter(this)
  implicit lazy val openApiRw: ReadWriter[OpenApi] = UpickleOpenApi.readWriter(this)
}

case object UpickleJsonCustom {

  /** Default data types support for uPickle message codec using JSON format. */
  lazy val default: UpickleJsonCustom = new UpickleJsonCustom {}
}
