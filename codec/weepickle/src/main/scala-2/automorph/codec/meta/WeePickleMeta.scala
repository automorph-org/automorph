package automorph.codec.meta

import automorph.spi.MessageCodec
import com.rallyhealth.weejson.v1.Value
import scala.language.experimental.macros
import scala.reflect.macros.blackbox

/** weePickle codec plugin code generation. */
trait WeePickleMeta extends MessageCodec[Value] {

  override def encode[T](value: T): Value =
    macro WeePickleMeta.encodeMacro[T]

  override def decode[T](node: Value): T =
    macro WeePickleMeta.decodeMacro[T]
}

object WeePickleMeta {

  def encodeMacro[T](c: blackbox.Context)(value: c.Expr[T]): c.Expr[Value] = {
    import c.universe.Quasiquote

    c.Expr[Value](q"""
      import automorph.codec.WeePickleCodec.*
      import com.rallyhealth.weejson.v1.Value
      import com.rallyhealth.weepickle.v1.WeePickle.FromScala
      FromScala($value).transform(Value)
    """)
  }

  def decodeMacro[T: c.WeakTypeTag](c: blackbox.Context)(node: c.Expr[Value]): c.Expr[T] = {
    import c.universe.{Quasiquote, weakTypeOf}

    c.Expr[T](q"""
      import automorph.codec.WeePickleCodec.*
      import com.rallyhealth.weepickle.v1.WeePickle.ToScala
      $node.transform(ToScala[${weakTypeOf[T]}])
    """)
  }
}
