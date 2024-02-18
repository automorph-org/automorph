package automorph.codec.meta

import automorph.spi.MessageCodec
import play.api.libs.json.JsValue
import scala.language.experimental.macros
import scala.reflect.macros.blackbox

/** Play JSON codec plugin code generation. */
trait PlayJsonMeta extends MessageCodec[JsValue] {

  override def encode[T](value: T): JsValue =
    macro PlayJsonMeta.encodeMacro[T]

  override def decode[T](node: JsValue): T =
    macro PlayJsonMeta.decodeMacro[T]
}

object PlayJsonMeta {

  def encodeMacro[T](c: blackbox.Context)(value: c.Expr[T]): c.Expr[JsValue] = {
    import c.universe.Quasiquote

    c.Expr[JsValue](q"""
      import automorph.codec.PlayJsonCodec.*
      import play.api.libs.json.Json
      Json.toJson($value)
    """)
  }

  def decodeMacro[T: c.WeakTypeTag](c: blackbox.Context)(node: c.Expr[JsValue]): c.Expr[T] = {
    import c.universe.{Quasiquote, weakTypeOf}

    c.Expr[T](q"""
      import automorph.codec.PlayJsonCodec.*
      import play.api.libs.json.{JsResult, Json}
      JsResult.toTry(Json.fromJson[${weakTypeOf[T]}]($node), JsResult.Exception.apply).get
    """)
  }
}
