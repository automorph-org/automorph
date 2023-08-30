package automorph.codec.json.meta

import automorph.codec.json.UpickleJsonConfig
import automorph.spi.MessageCodec
import scala.language.experimental.macros
import scala.reflect.macros.blackbox
import ujson.Value

/**
 * uPickle JSON codec plugin code generation.
 *
 * @tparam Config
 *   uPickle configuration type
 */
trait UpickleJsonMeta[Config <: UpickleJsonConfig] extends MessageCodec[Value] {

  override def encode[T](value: T): Value =
    macro UpickleJsonMeta.encodeMacro[T]

  override def decode[T](node: Value): T =
    macro UpickleJsonMeta.decodeMacro[T]
}

object UpickleJsonMeta {

  def encodeMacro[T: c.WeakTypeTag](c: blackbox.Context)(value: c.Expr[T]): c.Expr[Value] = {
    import c.universe.Quasiquote

    c.Expr[Value](q"""
      import ${c.prefix}.config.*
      ${c.prefix}.config.writeJs($value)
    """)
  }

  def decodeMacro[T: c.WeakTypeTag](c: blackbox.Context)(node: c.Expr[Value]): c.Expr[T] = {
    import c.universe.{weakTypeOf, Quasiquote}

    c.Expr[T](q"""
      import ${c.prefix}.config.*
      ${c.prefix}.config.read[${weakTypeOf[T]}]($node)
    """)
  }
}
