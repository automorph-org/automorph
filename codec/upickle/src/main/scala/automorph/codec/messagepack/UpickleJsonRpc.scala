package automorph.codec.messagepack

import automorph.protocol.jsonrpc.{Message, MessageError}
import upack.{Arr, Float64, Msg, Null, Obj, Str}
import upickle.core.{Abort, LinkedHashMap}

/** JSON-RPC protocol support for uPickle message codec using MessagePack format. */
private[automorph] case object UpickleJsonRpc {

  private[automorph] type RpcMessage = Message[Msg]

  def readWriter[Custom <: UpickleMessagePackCustom](custom: Custom): custom.ReadWriter[Message[Msg]] = {
    import custom.*

    implicit val idRw: ReadWriter[Option[Message.Id]] = readwriter[Msg].bimap[Option[Message.Id]](
      {
        case Some(Right(id)) => Str(id)
        case Some(Left(id)) => Float64(id.toDouble)
        case None => Null
      },
      {
        case Str(id) => Some(Right(id))
        case Float64(id) => Some(Left(BigDecimal(id)))
        case Null => None
        case id => throw Abort(s"Invalid request identifier: $id")
      },
    )
    implicit val paramsRw: ReadWriter[Option[Message.Params[Msg]]] = readwriter[Msg].bimap[Option[Message.Params[Msg]]](
      {
        case Some(Right(params)) => Obj(LinkedHashMap[Msg, Msg](params.map { case (key, value) =>
            Str(key) -> value
          }))
        case Some(Left(params)) => Arr(params: _*)
        case None => Null
      },
      {
        case Obj(params) => Some(Right(params.toMap.map {
            case (Str(key), value) => key -> value
            case _ => throw Abort(s"Invalid request parameters: $params")
          }))
        case Arr(params) => Some(Left(params.toList))
        case Null => None
        case params => throw Abort(s"Invalid request parameters: $params")
      },
    )
    implicit val messageErrorRw: custom.ReadWriter[MessageError[Msg]] = custom.macroRW
    Seq(idRw, paramsRw, messageErrorRw)
    custom.macroRW[Message[Msg]]
  }
}
