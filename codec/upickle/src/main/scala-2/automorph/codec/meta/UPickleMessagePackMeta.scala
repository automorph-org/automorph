package automorph.codec.meta

import automorph.codec.UPickleMessagePackCodec.MessagePackConfig
import automorph.spi.MessageCodec
import upack.Msg
import scala.language.experimental.macros
import scala.reflect.macros.blackbox

/**
 * uPickle MessagePack codec plugin code generation.
 *
 * @tparam Config
 *   uPickle configuration type
 */
trait UPickleMessagePackMeta[Config <: MessagePackConfig] extends MessageCodec[Msg] {

  override def encode[T](value: T): Msg =
    macro UPickleMessagePackMeta.encodeMacro[T]

  override def decode[T](node: Msg): T =
    macro UPickleMessagePackMeta.decodeMacro[T]
}

object UPickleMessagePackMeta {

  def encodeMacro[T](c: blackbox.Context)(value: c.Expr[T]): c.Expr[Msg] = {
    import c.universe.Quasiquote

    c.Expr[Msg](q"""
      import ${c.prefix}.config.*
      ${c.prefix}.config.writeMsg($value)
    """)
  }

  def decodeMacro[T: c.WeakTypeTag](c: blackbox.Context)(node: c.Expr[Msg]): c.Expr[T] = {
    import c.universe.{Quasiquote, weakTypeOf}

    c.Expr[T](q"""
      import ${c.prefix}.config.*
      ${c.prefix}.config.readBinary[${weakTypeOf[T]}]($node)
    """)
  }
}