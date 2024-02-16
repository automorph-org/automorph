package automorph.codec.messagepack

import automorph.protocol.webrpc.{Message, MessageError}
import upack.Msg

/** Web-RPC protocol support for uPickle message codec using MessagePack format. */
private[automorph] case object UPickleWebRpc {

  type RpcMessage = Message[Msg]

  @scala.annotation.nowarn("msg=never used")
  def readWriter[Config <: UpickleMessagePackConfig](config: Config): config.ReadWriter[Message[Msg]] = {
    import config.*

    implicit val messageErrorRw: config.ReadWriter[MessageError] = config.macroRW
    config.macroRW[Message[Msg]]
  }
}