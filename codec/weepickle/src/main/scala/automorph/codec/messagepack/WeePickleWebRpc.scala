package automorph.codec.messagepack

import automorph.protocol.webrpc.{Message, MessageError}
import com.rallyhealth.weepack.v1.Msg
import com.rallyhealth.weepickle.v1.WeePickle.{FromTo, macroFromTo}

/** Web-RPC protocol support for weePickle message codec using JSON format. */
private[automorph] object WeePickleWebRpc {

  type RpcMessage = Message[Msg]

  def fromTo: FromTo[RpcMessage] = {
    implicit val messageErrorFromTo: FromTo[MessageError] = macroFromTo
    macroFromTo[RpcMessage]
  }
}
