package automorph.codec

import automorph.protocol.webrpc.{Message, MessageError}
import com.rallyhealth.weejson.v1.Value
import com.rallyhealth.weepickle.v1.WeePickle.{FromTo, macroFromTo}

/** Web-RPC protocol support for weePickle message codec using JSON format. */
private[automorph] object WeePickleWebRpc {

  type RpcMessage = Message[Value]

  @scala.annotation.nowarn("msg=never used")
  def fromTo: FromTo[RpcMessage] = {
    implicit val messageErrorFromTo: FromTo[MessageError] = macroFromTo
    macroFromTo[RpcMessage]
  }
}
