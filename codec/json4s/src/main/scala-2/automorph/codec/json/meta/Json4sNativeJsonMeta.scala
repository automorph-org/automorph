package automorph.codec.json.meta

import automorph.spi.MessageCodec
import org.json4s.JValue
import scala.language.experimental.macros
import scala.reflect.macros.blackbox

/** Json4s JSON codec plugin code generation. */
private[automorph] trait Json4sNativeJsonMeta extends MessageCodec[JValue] {

  override def encode[T](value: T): JValue =
    macro Json4sNativeJsonMeta.encodeMacro[T]

  override def decode[T](node: JValue): T =
    macro Json4sNativeJsonMeta.decodeMacro[T]
}

private[automorph] object Json4sNativeJsonMeta {

  //      import automorph.codec.json.Json4sNativeJsonCodec.formats
  def encodeMacro[T: c.WeakTypeTag](c: blackbox.Context)(value: c.Expr[T]): c.Expr[JValue] = {
    import c.universe.{Quasiquote, weakTypeOf}

    c.Expr[JValue](q"""
      import org.json4s.Extraction
      Extraction.decompose($value)
    """)
  }

  def decodeMacro[T: c.WeakTypeTag](c: blackbox.Context)(node: c.Expr[JValue]): c.Expr[T] = {
    import c.universe.{Quasiquote, weakTypeOf}

    //      import automorph.codec.json.Json4sNativeJsonCodec.formats
    c.Expr[T](q"""
      import org.json4s.Extraction
      Extraction.extract[${weakTypeOf[T]}]($node)
    """)
  }
}
