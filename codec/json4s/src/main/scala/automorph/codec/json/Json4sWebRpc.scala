//package automorph.codec.json
//
//import automorph.protocol.webrpc.{Message, MessageError}
//import org.json4s.JValue
//import io.json4s.generic.semiauto.{deriveDecoder, deriveEncoder}
//import io.json4s.{Decoder, Encoder, Json}
//
///** Web-RPC protocol support for Json4s message codec plugin using JSON format. */
//private[automorph] object Json4sWebRpc {
//
//  type RpcMessage = Message[JValue]
//
//  def encoder: Encoder[Message[JValue]] = {
//    implicit val messageErrorEncoder: Encoder[MessageError] = deriveEncoder[MessageError]
//    deriveEncoder[Message[JValue]]
//  }
//
//  def decoder: Decoder[Message[JValue]] = {
//    implicit val messageErrorDecoder: Decoder[MessageError] = deriveDecoder[MessageError]
//    deriveDecoder[Message[JValue]]
//  }
//}
