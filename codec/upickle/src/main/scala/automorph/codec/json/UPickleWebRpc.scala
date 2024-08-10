package automorph.codec.json

import automorph.codec.UPickleJsonCodec.JsonConfig
import automorph.protocol.webrpc.{Message, MessageError}
import ujson.Value

/** Web-RPC protocol support for uPickle message codec using JSON format. */
private[automorph] object UPickleWebRpc {

  type RpcMessage = Message[Value]

  def readWriter[Config <: JsonConfig](config: Config): config.ReadWriter[Message[Value]] = {
    import config.*

    implicit val messageErrorRw: config.ReadWriter[MessageError] = config.macroRW
    config.macroRW[Message[Value]]
  }
}