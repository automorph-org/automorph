package automorph.codec.messagepack.meta

import automorph.codec.messagepack.UpickleMessagePackCustom
import automorph.spi.MessageCodec
import scala.language.experimental.macros
import scala.reflect.macros.blackbox
import upack.Msg

/**
 * UPickle MessagePack codec plugin code generation.
 *
 * @tparam Custom
 *   customized Upickle reader and writer implicits instance type
 */
trait UpickleMessagePackMeta[Custom <: UpickleMessagePackCustom] extends MessageCodec[Msg] {

  override def encode[T](value: T): Msg =
    macro UpickleMessagePackMeta.encodeMacro[T]

  override def decode[T](node: Msg): T =
    macro UpickleMessagePackMeta.decodeMacro[T]
}

case object UpickleMessagePackMeta {

  def encodeMacro[T](c: blackbox.Context)(value: c.Expr[T]): c.Expr[Msg] = {
    import c.universe.Quasiquote

    c.Expr[Msg](q"""
      import ${c.prefix}.custom.*
      ${c.prefix}.custom.writeMsg($value)
    """)
  }

  def decodeMacro[T: c.WeakTypeTag](c: blackbox.Context)(node: c.Expr[Msg]): c.Expr[T] = {
    import c.universe.{weakTypeOf, Quasiquote}

    c.Expr[T](q"""
      import ${c.prefix}.custom.*
      ${c.prefix}.custom.readBinary[${weakTypeOf[T]}]($node)
    """)
  }
}
