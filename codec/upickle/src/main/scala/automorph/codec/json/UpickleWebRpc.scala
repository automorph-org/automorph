package automorph.codec.json

import automorph.protocol.webrpc.{Message, MessageError}
import ujson.Value

/** Web-RPC protocol support for uPickle message codec using JSON format. */
private[automorph] object UpickleWebRpc {

  type RpcMessage = Message[Value]

  @scala.annotation.nowarn("msg=never used")
  def readWriter[Config <: UpickleJsonConfig](config: Config): config.ReadWriter[Message[Value]] = {
    import config.*

    implicit val messageErrorRw: config.ReadWriter[MessageError] = config.macroRW
    config.macroRW[Message[Value]]
  }
}
