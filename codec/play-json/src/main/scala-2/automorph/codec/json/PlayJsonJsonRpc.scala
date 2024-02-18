package automorph.codec.json

import automorph.protocol.jsonrpc.{Message, MessageError}
import play.api.libs.json.{JsArray, JsNumber, JsObject, JsString, JsValue, Json, Reads, Writes}

/** JSON-RPC protocol support for Play JSON message codec using JSON format. */
private[automorph] object PlayJsonJsonRpc {

  type RpcMessage = Message[JsValue]

  @scala.annotation.nowarn("msg=never used")
  val reads: Reads[Message[JsValue]] = {
    implicit val idReads: Reads[Either[BigDecimal, String]] =
      (json: JsValue) => json.validate[BigDecimal].map(Left.apply).orElse(json.validate[String].map(Right.apply))
    implicit val paramsReads: Reads[Either[List[JsValue], Map[String, JsValue]]] =
      (json: JsValue) =>
        json.validate[List[JsValue]].map(Left.apply).orElse(json.validate[Map[String, JsValue]].map(Right.apply))
    implicit val messageErrorReads: Reads[MessageError[JsValue]] = Json.reads
    Json.reads
  }
  @scala.annotation.nowarn("msg=never used")
  val writes: Writes[Message[JsValue]] = {
    implicit val idWrites: Writes[Either[BigDecimal, String]] = {
      case Left(value) => JsNumber(value)
      case Right(value) => JsString(value)
    }
    implicit val paramsWrites: Writes[Either[List[JsValue], Map[String, JsValue]]] = {
      case Left(value) => JsArray(value)
      case Right(value) => JsObject(value)
    }
    implicit val messageErrorWrites: Writes[MessageError[JsValue]] = Json.writes
    Json.writes
  }
}
