package automorph.codec

import automorph.codec.JacksonRpcProtocol.{field, serializer}
import automorph.protocol.webrpc.{Message, MessageError}
import com.fasterxml.jackson.core.{JsonParseException, JsonParser}
import com.fasterxml.jackson.databind.deser.std.StdDeserializer
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.databind.{DeserializationContext, JsonNode}

/** Web-RPC protocol support for Jackson message codec plugin using JSON format. */
private[automorph] object JacksonWebRpc {

  type RpcMessage = Message[JsonNode]
  type RpcError = MessageError

  def module: SimpleModule = {
    val rpcErrorClass = classOf[JacksonWebRpc.RpcError]
    val rpcMessageClass = classOf[JacksonWebRpc.RpcMessage]
    new SimpleModule().addSerializer(rpcErrorClass, serializer(rpcErrorClass))
      .addDeserializer(rpcErrorClass, JacksonWebRpc.messageErrorDeserializer)
      .addSerializer(rpcMessageClass, serializer(rpcMessageClass))
      .addDeserializer(rpcMessageClass, JacksonWebRpc.messageDeserializer)
  }

  private def messageErrorDeserializer =
    new StdDeserializer[RpcError](classOf[RpcError]) {

      override def deserialize(parser: JsonParser, context: DeserializationContext): RpcError =
        context.readTree(parser) match {
          case node: ObjectNode => MessageError(
              field(Message.message, value => Option.when(value.isTextual)(value.asText), node, parser),
              field(Message.code, value => Option.when(value.isInt)(value.asInt), node, parser),
            )
          case _ => throw new JsonParseException(parser, "Invalid message error", parser.currentLocation)
        }
    }

  private def messageDeserializer =
    new StdDeserializer[RpcMessage](classOf[RpcMessage]) {

      override def deserialize(parser: JsonParser, context: DeserializationContext): RpcMessage =
        context.readTree(parser) match {
          case node: ObjectNode => Message[JsonNode](
              field(Message.result, Some(_), node, parser),
              field(
                Message.error,
                value => Some(context.readValue[RpcError](value.traverse(), classOf[RpcError])),
                node,
                parser,
              ),
            )
          case _ => throw new JsonParseException(parser, "Invalid message", parser.currentLocation)
        }
    }
}
