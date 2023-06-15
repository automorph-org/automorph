package automorph.codec.json

import automorph.protocol.webrpc.{Message, MessageError}
import ujson.Value

/** Web-RPC protocol support for uPickle message codec using JSON format. */
private[automorph] case object UpickleWebRpc {

  type RpcMessage = Message[Value]

  def readWriter[Custom <: UpickleJsonCustom](custom: Custom): custom.ReadWriter[Message[Value]] = {
    import custom.*

    implicit val messageErrorRw: custom.ReadWriter[MessageError] = custom.macroRW
    custom.macroRW[Message[Value]]
  }
}
