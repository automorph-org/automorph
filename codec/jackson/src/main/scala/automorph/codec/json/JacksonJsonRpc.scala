package automorph.codec.json

import automorph.protocol.jsonrpc.{Message, MessageError}
import com.fasterxml.jackson.core.{JsonGenerator, JsonParseException, JsonParser}
import com.fasterxml.jackson.databind.deser.std.StdDeserializer
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.databind.node.{ArrayNode, ObjectNode}
import com.fasterxml.jackson.databind.ser.std.StdSerializer
import com.fasterxml.jackson.databind.{DeserializationContext, JsonNode, SerializerProvider}
import scala.jdk.CollectionConverters.IteratorHasAsScala

/** JSON-RPC protocol support for Jackson message codec plugin using JSON format. */
private[automorph] object JacksonJsonRpc {

  type RpcMessage = Message[JsonNode]
  type RpcError = MessageError[JsonNode]

  def module =
    new SimpleModule().addSerializer(classOf[JacksonJsonRpc.RpcError], JacksonJsonRpc.messageErrorSerializer)
      .addDeserializer(classOf[JacksonJsonRpc.RpcError], JacksonJsonRpc.messageErrorDeserializer)
      .addSerializer(classOf[JacksonJsonRpc.RpcMessage], JacksonJsonRpc.messageSerializer)
      .addDeserializer(classOf[JacksonJsonRpc.RpcMessage], JacksonJsonRpc.messageDeserializer)

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
          case node: ObjectNode => MessageError[JsonNode](
              field("message", value => Option.when(value.isTextual)(value.asText), node, parser),
              field("code", value => Option.when(value.isInt)(value.asInt), node, parser),
              field("data", Some(_), node, parser),
            )
          case _ => throw new JsonParseException(parser, "Invalid message error", parser.getCurrentLocation)
        }
    }

  private def messageSerializer =
    new StdSerializer[RpcMessage](classOf[RpcMessage]) {

      override def serialize(value: RpcMessage, generator: JsonGenerator, provider: SerializerProvider): Unit = {
        val entries = value.productElementNames.zip(value.productIterator).flatMap {
          case (name, Some(Right(value))) => Some(name -> value)
          case (name, Some(Left(value))) => Some(name -> value)
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
              field("jsonrpc", value => Option.when(value.isTextual)(value.asText), node, parser),
              field(
                "id",
                _ match {
                  case value if value.isTextual => Some(Right(value.asText))
                  case value if value.isNumber => Some(Left(BigDecimal(value.asDouble)))
                  case _ => None
                },
                node,
                parser,
              ),
              field("method", value => Option.when(value.isTextual)(value.asText), node, parser),
              field(
                "params",
                _ match {
                  case value if value.isObject =>
                    val params = value.asInstanceOf[ObjectNode].fields().asScala
                    Some(Right(params.map(field => field.getKey -> field.getValue).toMap))
                  case value if value.isArray =>
                    val params = value.asInstanceOf[ArrayNode].elements().asScala
                    Some(Left(params.toList))
                  case _ => None
                },
                node,
                parser,
              ),
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
