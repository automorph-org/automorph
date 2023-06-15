package automorph.codec.json

import automorph.protocol.webrpc.{Message, MessageError}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.{Decoder, Encoder, Json}

/** Web-RPC protocol support for Circe message codec plugin using JSON format. */
private[automorph] case object CirceWebRpc {

  type RpcMessage = Message[Json]

  def messageEncoder: Encoder[Message[Json]] = {
    implicit val messageErrorEncoder: Encoder[MessageError] = deriveEncoder[MessageError]

    deriveEncoder[Message[Json]]
  }

  def messageDecoder: Decoder[Message[Json]] = {
    implicit val messageErrorDecoder: Decoder[MessageError] = deriveDecoder[MessageError]

    deriveDecoder[Message[Json]]
  }
}
