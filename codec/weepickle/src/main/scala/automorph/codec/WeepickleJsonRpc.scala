package automorph.codec

import automorph.protocol.jsonrpc.{Message, MessageError}
import com.rallyhealth.weejson.v1.{Arr, Null, Num, Obj, Str, Value}
import com.rallyhealth.weepickle.v1.WeePickle.{FromTo, macroFromTo}
import com.rallyhealth.weepickle.v1.core.Abort
import scala.annotation.nowarn

/** JSON-RPC protocol support for weePickle message codec using JSON format. */
private[automorph] object WeepickleJsonRpc {

  type RpcMessage = Message[Value]

  @nowarn("msg=used")
  def fromTo: FromTo[Message[Value]] = {
    implicit val idFromTo: FromTo[Option[Message.Id]] = macroFromTo[Value].bimap[Option[Message.Id]](
      {
        case Some(Right(id)) => Str(id)
        case Some(Left(id)) => Num(id.toDouble)
        case None => Null
      },
      {
        case Str(id) => Some(Right(id))
        case Num(id) => Some(Left(id))
        case Null => None
        case id => throw new Abort(s"Invalid request identifier: $id")
      },
    )
    implicit val paramsFromTo: FromTo[Option[Message.Params[Value]]] = macroFromTo[Value]
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
          case params => throw new Abort(s"Invalid request parameters: $params")
        },
      )
    implicit val messageErrorFromTo: FromTo[MessageError[Value]] = macroFromTo
    macroFromTo[Message[Value]]
  }
}
