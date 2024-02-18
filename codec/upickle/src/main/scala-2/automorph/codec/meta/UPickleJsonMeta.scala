package automorph.codec.meta

import automorph.codec.UPickleJsonCodec.JsonConfig
import automorph.spi.MessageCodec
import ujson.Value
import scala.language.experimental.macros
import scala.reflect.macros.blackbox

/**
 * uPickle JSON codec plugin code generation.
 *
 * @tparam Config
 *   uPickle configuration type
 */
trait UPickleJsonMeta[Config <: JsonConfig] extends MessageCodec[Value] {

  override def encode[T](value: T): Value =
    macro UPickleJsonMeta.encodeMacro[T]

  override def decode[T](node: Value): T =
    macro UPickleJsonMeta.decodeMacro[T]
}

object UPickleJsonMeta {

  def encodeMacro[T](c: blackbox.Context)(value: c.Expr[T]): c.Expr[Value] = {
    import c.universe.Quasiquote

    c.Expr[Value](q"""
      import ${c.prefix}.config.*
      ${c.prefix}.config.writeJs($value)
    """)
  }

  def decodeMacro[T: c.WeakTypeTag](c: blackbox.Context)(node: c.Expr[Value]): c.Expr[T] = {
    import c.universe.{Quasiquote, weakTypeOf}

    c.Expr[T](q"""
      import ${c.prefix}.config.*
      ${c.prefix}.config.read[${weakTypeOf[T]}]($node)
    """)
  }
}
