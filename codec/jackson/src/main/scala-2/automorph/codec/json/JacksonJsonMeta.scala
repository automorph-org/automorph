package automorph.codec.json

import automorph.spi.MessageCodec
import com.fasterxml.jackson.databind.{JsonNode, ObjectMapper}
import scala.language.experimental.macros
import scala.reflect.macros.blackbox

/** Jackson JSON codec plugin code generation. */
private[automorph] trait JacksonJsonMeta extends MessageCodec[JsonNode] {

  def objectMapper: ObjectMapper

  override def encode[T](value: T): JsonNode =
    objectMapper.valueToTree(value)

  override def decode[T](node: JsonNode): T =
    macro JacksonJsonMeta.decodeExpr[T]
}

private[automorph] case object JacksonJsonMeta {

  def decodeExpr[T: c.WeakTypeTag](c: blackbox.Context)(node: c.Expr[JsonNode]): c.Expr[T] = {
    import c.universe.{Quasiquote, weakTypeOf}

    c.Expr[T](q"""
      ${c.prefix}.objectMapper.treeToValue($node, classOf[${weakTypeOf[T]}])
    """)
  }
}
