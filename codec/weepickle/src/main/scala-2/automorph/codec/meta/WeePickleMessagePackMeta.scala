package automorph.codec.meta

import automorph.spi.MessageCodec
import com.rallyhealth.weepack.v1.Msg
import scala.language.experimental.macros
import scala.reflect.macros.blackbox

/** weePickle MessagePack codec plugin code generation. */
trait WeePickleMessagePackMeta extends MessageCodec[Msg] {

  override def encode[T](value: T): Msg =
    macro WeePickleMessagePackMeta.encodeMacro[T]

  override def decode[T](node: Msg): T =
    macro WeePickleMessagePackMeta.decodeMacro[T]
}

object WeePickleMessagePackMeta {

  def encodeMacro[T](c: blackbox.Context)(value: c.Expr[T]): c.Expr[Msg] = {
    import c.universe.Quasiquote

    c.Expr[Msg](q"""
      import automorph.codec.WeePickleMessagePackCodec.*
      import com.rallyhealth.weepack.v1.Msg
      import com.rallyhealth.weepickle.v1.WeePickle.FromScala
      FromScala($value).transform(Msg)
    """)
  }

  def decodeMacro[T: c.WeakTypeTag](c: blackbox.Context)(node: c.Expr[Msg]): c.Expr[T] = {
    import c.universe.{Quasiquote, weakTypeOf}

    c.Expr[T](q"""
      import automorph.codec.WeePickleMessagePackCodec.*
      import com.rallyhealth.weepickle.v1.WeePickle.ToScala
      $node.transform(ToScala[${weakTypeOf[T]}])
    """)
  }
}
