package automorph.codec.json

import automorph.spi.MessageCodec
import com.fasterxml.jackson.databind.{JsonNode, ObjectMapper}
import scala.compiletime.summonInline
import scala.reflect.ClassTag

/** Jackson JSON codec plugin code generation. */
private[automorph] trait JacksonJsonMeta extends MessageCodec[JsonNode]:

  def objectMapper: ObjectMapper

  override inline def encode[T](value: T): JsonNode =
    objectMapper.valueToTree(value)

  override inline def decode[T](node: JsonNode): T =
    val classTag = summonInline[ClassTag[T]]
    val valueClass = classTag.runtimeClass.asInstanceOf[Class[T]]
    objectMapper.treeToValue(node, valueClass)
