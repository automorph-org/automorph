package automorph.codec.messagepack

import automorph.protocol.webrpc.{Message, MessageError}
import upack.Msg

/** Web-RPC protocol support for uPickle message codec using MessagePack format. */
private[automorph] case object UpickleWebRpc {

  type RpcMessage = Message[Msg]

  def readWriter[Config <: UpickleMessagePackConfig](config: Config): config.ReadWriter[Message[Msg]] = {
    import config.*

    implicit val messageErrorRw: config.ReadWriter[MessageError] = config.macroRW
    Seq(messageErrorRw)
    config.macroRW[Message[Msg]]
  }
}
