package automorph.codec.json

import argonaut.Argonaut.{jArray, jNumber, jObject, jString}
import argonaut.{Argonaut, CodecJson, DecodeResult, Json, JsonObject}
import automorph.protocol.jsonrpc.{Message, MessageError}

/** JSON-RPC protocol support for uPickle message codec plugin using JSON format. */
private[automorph] case object ArgonautJsonRpc {

  type RpcMessage = Message[Json]

  def messageCodecJson: CodecJson[Message[Json]] = {
    implicit val idCodecJson: CodecJson[Message.Id] = CodecJson(
      {
        case Right(id) => jString(id)
        case Left(id) => jNumber(id)
      },
      cursor =>
        DecodeResult(
          cursor.focus.string.map(Right.apply)
            .orElse(cursor.focus.number.map(number => Left(number.toBigDecimal))) match {
            case Some(value) => Right(value)
            case None => Left(s"Invalid request identifier: ${cursor.focus}", cursor.history)
          }
        ),
    )
    implicit val paramsCodecJson: CodecJson[Message.Params[Json]] = CodecJson(
      {
        case Right(params) => jObject(JsonObject.fromIterable(params))
        case Left(params) => jArray(params)
      },
      cursor =>
        DecodeResult(
          cursor.focus.obj.map(json => Right(json.toMap)).orElse(cursor.focus.array.map(Left.apply)) match {
            case Some(value) => Right(value)
            case None => Left(s"Invalid request parameters: ${cursor.focus}", cursor.history)
          }
        ),
    )
    implicit val messageErrorCodecJson: CodecJson[MessageError[Json]] = Argonaut.codec3(
      MessageError.apply[Json],
      (v: MessageError[Json]) => (v.message, v.code, v.data),
    )("message", "code", "data")

    Argonaut.codec6(
      Message.apply[Json],
      (v: Message[Json]) => (v.jsonrpc, v.id, v.method, v.params, v.result, v.error),
    )("jsonrpc", "id", "method", "params", "result", "error")
  }
}
