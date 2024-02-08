//package automorph.codec.json
//
//import automorph.protocol.jsonrpc.{Message, MessageError}
//import org.json4s.JValue
//import io.json4s.generic.semiauto.{deriveDecoder, deriveEncoder}
//import io.json4s.{Decoder, Encoder, Json, JsonObject}
//
///** JSON-RPC protocol support for Json4s message codec plugin using JSON format. */
//private[automorph] object Json4sJsonRpc {
//
//  type RpcMessage = Message[JValue]
//
//  def encoder: Encoder[Message[JValue]] = {
//    implicit val idEncoder: Encoder[Message.Id] = Encoder.encodeJValue.contramap[Message.Id] {
//      case Right(id) => JValue.fromString(id)
//      case Left(id) => JValue.fromBigDecimal(id)
//    }
//    implicit val paramsEncoder: Encoder[Message.Params[JValue]] = Encoder.encodeJValue.contramap[Message.Params[JValue]] {
//      case Right(params) => JValue.fromJValueObject(JValueObject.fromMap(params))
//      case Left(params) => JValue.fromValues(params)
//    }
//    implicit val messageErrorEncoder: Encoder[MessageError[JValue]] = deriveEncoder[MessageError[JValue]]
//    deriveEncoder[Message[JValue]]
//  }
//
//  def decoder: Decoder[Message[JValue]] = {
//    implicit val idDecoder: Decoder[Message.Id] = Decoder.decodeJValue.map(
//      _.fold(
//        invalidId(None.orNull),
//        invalidId,
//        id => id.toBigDecimal.map(Left.apply).getOrElse(invalidId(id)),
//        id => Right(id),
//        invalidId,
//        invalidId,
//      )
//    )
//    implicit val paramsDecoder: Decoder[Message.Params[JValue]] = Decoder.decodeJValue.map(
//      _.fold(
//        invalidParams(None.orNull),
//        invalidParams,
//        invalidParams,
//        invalidParams,
//        params => Left(params.toList),
//        params => Right(params.toMap),
//      )
//    )
//    implicit val messageErrorDecoder: Decoder[MessageError[JValue]] = deriveDecoder[MessageError[JValue]]
//    deriveDecoder[Message[JValue]]
//  }
//
//  private def invalidId(value: Any): Message.Id =
//    throw new IllegalArgumentException(s"Invalid request identifier: $value")
//
//  private def invalidParams(value: Any): Message.Params[JValue] =
//    throw new IllegalArgumentException(s"Invalid request parameters: $value")
//}
