package automorph.codec.messagepack

import automorph.codec.UPickleMessagePackCodec.MessagePackConfig
import automorph.protocol.webrpc.{Message, MessageError}
import upack.Msg

/** Web-RPC protocol support for uPickle message codec using MessagePack format. */
private[automorph] object UPickleWebRpc {

  type RpcMessage = Message[Msg]

  @scala.annotation.nowarn("msg=never used")
  def readWriter[Config <: MessagePackConfig](config: Config): config.ReadWriter[Message[Msg]] = {
    import config.*

    implicit val messageErrorRw: config.ReadWriter[MessageError] = config.macroRW
    config.macroRW[Message[Msg]]
  }
}
