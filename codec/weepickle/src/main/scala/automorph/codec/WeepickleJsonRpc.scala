package automorph.codec

import automorph.protocol.jsonrpc.{Message, MessageError}
import com.rallyhealth.weejson.v1.{Arr, Null, Num, Obj, Str, Value}
import com.rallyhealth.weepickle.v1.WeePickle.{From, FromTo, FromValue, To, ToValue, macroFromTo}
import com.rallyhealth.weepickle.v1.core.Abort

/** JSON-RPC protocol support for weePickle message codec using JSON format. */
private[automorph] object WeepickleJsonRpc {

  type RpcMessage = Message[Value]

  def fromTo: FromTo[Message[Value]] = {
    implicit val idFrom: From[Option[Message.Id]] = FromValue.comap {
      case Some(Right(id)) => Str(id)
      case Some(Left(id)) => Num(id.toDouble)
      case None => Null
    }
    implicit val idTo: To[Option[Message.Id]] = ToValue.map {
      case Str(id) => Some(Right(id))
      case Num(id) => Some(Left(id))
      case Null => None
      case id => throw new Abort(s"Invalid request identifier: $id")
    }
    implicit val paramsFrom: From[Option[Message.Params[Value]]] = FromValue.comap {
      case Some(Right(params)) => Obj.from(params)
      case Some(Left(params)) => Arr(params)
      case None => Null
    }
    implicit val paramsTo: To[Option[Message.Params[Value]]] = ToValue.map {
      case Obj(params) => Some(Right(params.toMap))
      case Arr(params) => Some(Left(params.toList))
      case Null => None
      case params => throw new Abort(s"Invalid request parameters: $params")
    }
    implicit val messageErrorFromTo: FromTo[MessageError[Value]] = macroFromTo
    macroFromTo[Message[Value]]
  }
}
