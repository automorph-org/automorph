package automorph.codec.json.meta

import automorph.spi.MessageCodec
import org.json4s.{Formats, JValue}
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

  def encodeMacro[T: c.WeakTypeTag](c: blackbox.Context)(value: c.Expr[T]): c.Expr[JValue] = {
    import c.universe.{Quasiquote, weakTypeOf}

    //      Extraction.decompose($value)(implicitly[Formats])
    //      Extraction.decompose($value)
    c.Expr[JValue](q"""
      import automorph.codec.json.Json4sNativeJsonCodec.*
      import org.json4s.Extraction
      import org.json4s.Formats
      import org.json4s.DefaultFormats
      import org.json4s.native.JsonMethods.*
      null.asInstanceOf[${weakTypeOf[JValue]}]
    """)
  }

  //  Extraction.extract[${weakTypeOf[T]}]($node)
  def decodeMacro[T: c.WeakTypeTag](c: blackbox.Context)(node: c.Expr[JValue]): c.Expr[T] = {
    import c.universe.{Quasiquote, weakTypeOf}

    c.Expr[T](q"""
      import automorph.codec.json.Json4sNativeJsonCodec.*
      import org.json4s.Extraction
      import org.json4s.DefaultFormats
      import org.json4s.native.JsonMethods.*
      null.asInstanceOf[${weakTypeOf[T]}]
    """)
  }
}
