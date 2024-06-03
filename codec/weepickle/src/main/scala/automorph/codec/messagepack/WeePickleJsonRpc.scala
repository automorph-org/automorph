package automorph.codec.messagepack

import automorph.protocol.jsonrpc.{Message, MessageError}
import com.rallyhealth.weepack.v1.Msg.{FromMsgValue, ToMsgValue}
import com.rallyhealth.weepack.v1.{Arr, Float64, Msg, Null, Obj, Str}
import com.rallyhealth.weepickle.v1.WeePickle.{From, FromTo, To, macroFromTo}
import com.rallyhealth.weepickle.v1.core.Abort
import scala.collection.mutable

/** JSON-RPC protocol support for weePickle message codec using JSON format. */
private[automorph] object WeePickleJsonRpc {

  type RpcMessage = Message[Msg]

  def fromTo: FromTo[Message[Msg]] = {
    implicit val idFrom: From[Option[Message.Id]] = FromMsgValue.comap {
      case Some(Right(id)) => Str(id)
      case Some(Left(id)) => Float64(id.toDouble)
      case None => Null
    }
    implicit val idTo: To[Option[Message.Id]] = ToMsgValue.map {
      case Str(id) => Some(Right(id))
      case Float64(id) => Some(Left(id))
      case Null => None
      case id => throw new Abort(s"Invalid request identifier: $id")
    }
    implicit val paramsFrom: From[Option[Message.Params[Msg]]] = FromMsgValue.comap {
      case Some(Right(params)) => Obj(mutable.LinkedHashMap.newBuilder[Msg, Msg].addAll(params.map {
          case (key, value) => Str(key) -> value
        }).result())
      case Some(Left(params)) => Arr(params*)
      case None => Null
    }
    implicit val paramsTo: To[Option[Message.Params[Msg]]] = ToMsgValue.map {
      case Obj(params) => Some(Right(params.map { case (key, value) =>
          key.str -> value
        }.toMap))
      case Arr(params) => Some(Left(params.toList))
      case Null => None
      case params => throw new Abort(s"Invalid request parameters: $params")
    }
    implicit val messageErrorFromTo: FromTo[MessageError[Msg]] = macroFromTo
    macroFromTo[Message[Msg]]
  }
}
