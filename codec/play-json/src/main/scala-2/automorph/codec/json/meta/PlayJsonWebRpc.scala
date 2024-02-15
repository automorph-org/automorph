package automorph.codec.json.meta

import automorph.protocol.webrpc.{Message, MessageError}
import play.api.libs.json.{JsValue, Json, Reads, Writes}

/** Web-RPC protocol support for Play JSON message codec using JSON format. */
private[automorph] object PlayJsonWebRpc {

  type RpcMessage = Message[JsValue]

  @scala.annotation.nowarn("msg=never used")
  val reads: Reads[RpcMessage] = {
    implicit val messageErrorReads: Reads[MessageError] = Json.reads
    Json.reads
  }
  @scala.annotation.nowarn("msg=never used")
  val writes: Writes[RpcMessage] = {
    implicit val messageErrorWrites: Writes[MessageError] = Json.writes
    Json.writes
  }
}
