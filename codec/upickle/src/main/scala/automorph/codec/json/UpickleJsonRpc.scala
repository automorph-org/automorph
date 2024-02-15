package automorph.codec.json

import automorph.protocol.jsonrpc.{Message, MessageError}
import ujson.{Arr, Null, Num, Obj, Str, Value}
import upickle.core.Abort

/** JSON-RPC protocol support for uPickle message codec using JSON format. */
private[automorph] object UpickleJsonRpc {

  type RpcMessage = Message[Value]

  @scala.annotation.nowarn("msg=never used")
  def readWriter[Config <: UpickleJsonConfig](config: Config): config.ReadWriter[Message[Value]] = {
    import config.*

    implicit val idRw: ReadWriter[Option[Message.Id]] = readwriter[Value].bimap[Option[Message.Id]](
      {
        case Some(Right(id)) => Str(id)
        case Some(Left(id)) => Num(id.toDouble)
        case None => Null
      },
      {
        case Str(id) => Some(Right(id))
        case Num(id) => Some(Left(BigDecimal(id)))
        case Null => None
        case id => throw Abort(s"Invalid request identifier: $id")
      },
    )
    implicit val paramsRw: ReadWriter[Option[Message.Params[Value]]] = readwriter[Value]
      .bimap[Option[Message.Params[Value]]](
        {
          case Some(Right(params)) => Obj.from(params)
          case Some(Left(params)) => Arr(params)
          case None => Null
        },
        {
          case Obj(params) => Some(Right(params.toMap))
          case Arr(params) => Some(Left(params.toList))
          case Null => None
          case params => throw Abort(s"Invalid request parameters: $params")
        },
      )
    implicit val messageErrorRw: config.ReadWriter[MessageError[Value]] = config.macroRW
    config.macroRW[Message[Value]]
  }
}
