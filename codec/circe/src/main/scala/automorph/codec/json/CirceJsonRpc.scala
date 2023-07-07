package automorph.codec.json

import automorph.protocol.jsonrpc.{Message, MessageError}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.{Decoder, Encoder, Json, JsonObject}

/** JSON-RPC protocol support for Circe message codec plugin using JSON format. */
private[automorph] object CirceJsonRpc {

  type RpcMessage = Message[Json]

  def messageEncoder: Encoder[Message[Json]] = {
    implicit val idEncoder: Encoder[Message.Id] = Encoder.encodeJson.contramap[Message.Id] {
      case Right(id) => Json.fromString(id)
      case Left(id) => Json.fromBigDecimal(id)
    }
    implicit val paramsEncoder: Encoder[Message.Params[Json]] = Encoder.encodeJson.contramap[Message.Params[Json]] {
      case Right(params) => Json.fromJsonObject(JsonObject.fromMap(params))
      case Left(params) => Json.fromValues(params)
    }
    implicit val messageErrorEncoder: Encoder[MessageError[Json]] = deriveEncoder[MessageError[Json]]

    deriveEncoder[Message[Json]]
  }

  def messageDecoder: Decoder[Message[Json]] = {
    implicit val idDecoder: Decoder[Message.Id] = Decoder.decodeJson.map(
      _.fold(
        invalidId(None.orNull),
        invalidId,
        id => id.toBigDecimal.map(Left.apply).getOrElse(invalidId(id)),
        id => Right(id),
        invalidId,
        invalidId,
      )
    )
    implicit val paramsDecoder: Decoder[Message.Params[Json]] = Decoder.decodeJson.map(
      _.fold(
        invalidParams(None.orNull),
        invalidParams,
        invalidParams,
        invalidParams,
        params => Left(params.toList),
        params => Right(params.toMap),
      )
    )
    implicit val messageErrorDecoder: Decoder[MessageError[Json]] = deriveDecoder[MessageError[Json]]

    deriveDecoder[Message[Json]]
  }

  private def invalidId(value: Any): Message.Id =
    throw new IllegalArgumentException(s"Invalid request identifier: $value")

  private def invalidParams(value: Any): Message.Params[Json] =
    throw new IllegalArgumentException(s"Invalid request parameters: $value")
}
