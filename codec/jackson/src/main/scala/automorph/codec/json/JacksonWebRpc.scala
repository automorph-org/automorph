package automorph.codec.json

import automorph.protocol.webrpc.{Message, MessageError}
import com.fasterxml.jackson.core.{JsonGenerator, JsonParseException, JsonParser}
import com.fasterxml.jackson.databind.deser.std.StdDeserializer
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.databind.ser.std.StdSerializer
import com.fasterxml.jackson.databind.{DeserializationContext, JsonNode, SerializerProvider}

/** Web-RPC protocol support for Jackson message codec plugin using JSON format. */
private[automorph] object JacksonWebRpc {

  type RpcMessage = Message[JsonNode]
  type RpcError = MessageError

  def module: SimpleModule =
    new SimpleModule().addSerializer(classOf[JacksonWebRpc.RpcError], JacksonWebRpc.messageErrorSerializer)
      .addDeserializer(classOf[JacksonWebRpc.RpcError], JacksonWebRpc.messageErrorDeserializer)
      .addSerializer(classOf[JacksonWebRpc.RpcMessage], JacksonWebRpc.messageSerializer)
      .addDeserializer(classOf[JacksonWebRpc.RpcMessage], JacksonWebRpc.messageDeserializer)

  private def messageErrorSerializer =
    new StdSerializer[RpcError](classOf[RpcError]) {

      override def serialize(value: RpcError, generator: JsonGenerator, provider: SerializerProvider): Unit = {
        val entries = value.productElementNames.zip(value.productIterator).flatMap {
          case (name, Some(value)) => Some(name -> value)
          case (_, None) => None
        }.toMap
        generator.writeObject(entries)
      }
    }

  private def messageErrorDeserializer =
    new StdDeserializer[RpcError](classOf[RpcError]) {

      override def deserialize(parser: JsonParser, context: DeserializationContext): RpcError =
        context.readTree(parser) match {
          case node: ObjectNode => MessageError(
              field("message", value => Option.when(value.isTextual)(value.asText), node, parser),
              field("code", value => Option.when(value.isInt)(value.asInt), node, parser),
            )
          case _ => throw new JsonParseException(parser, "Invalid message error", parser.getCurrentLocation)
        }
    }

  private def messageSerializer =
    new StdSerializer[RpcMessage](classOf[RpcMessage]) {

      override def serialize(value: RpcMessage, generator: JsonGenerator, provider: SerializerProvider): Unit = {
        val entries = value.productElementNames.zip(value.productIterator).flatMap {
          case (name, Some(value)) => Some(name -> value)
          case (_, None) => None
        }.toMap
        generator.writeObject(entries)
      }
    }

  private def messageDeserializer =
    new StdDeserializer[RpcMessage](classOf[RpcMessage]) {

      override def deserialize(parser: JsonParser, context: DeserializationContext): RpcMessage =
        context.readTree(parser) match {
          case node: ObjectNode => Message[JsonNode](
              field("result", Some(_), node, parser),
              field(
                "error",
                value => Some(context.readValue[RpcError](value.traverse(), classOf[RpcError])),
                node,
                parser,
              ),
            )
          case _ => throw new JsonParseException(parser, "Invalid message", parser.getCurrentLocation)
        }
    }

  private def field[T](name: String, extract: JsonNode => Option[T], node: ObjectNode, parser: JsonParser): Option[T] =
    Option(node.get(name)).filter(!_.isNull).map(extract).map(_.getOrElse {
      throw new JsonParseException(parser, s"Invalid $name", parser.getCurrentLocation)
    })
}
