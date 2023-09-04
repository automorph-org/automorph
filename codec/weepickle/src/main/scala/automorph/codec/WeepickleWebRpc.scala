package automorph.codec

import automorph.protocol.webrpc.{Message, MessageError}
import com.rallyhealth.weejson.v1.Value
import com.rallyhealth.weepickle.v1.WeePickle.{FromTo, macroFromTo}
import scala.annotation.nowarn

/** Web-RPC protocol support for weePickle message codec using JSON format. */
private[automorph] object WeepickleWebRpc {

  type RpcMessage = Message[Value]

  @nowarn("msg=used")
  def fromTo: FromTo[Message[Value]] = {
    implicit val messageErrorFromTo: FromTo[MessageError] = macroFromTo
    macroFromTo[Message[Value]]
  }
}
