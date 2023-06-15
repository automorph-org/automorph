package automorph.codec.json.meta

import argonaut.Json
import automorph.spi.MessageCodec
import scala.language.experimental.macros
import scala.reflect.macros.blackbox

/** Argonaut JSON codec plugin code generation. */
private[automorph] trait ArgonautJsonMeta extends MessageCodec[Json] {

  override def encode[T](value: T): Json =
    macro ArgonautJsonMeta.encodeExpr[T]

  override def decode[T](node: Json): T =
    macro ArgonautJsonMeta.decodeExpr[T]
}

private[automorph] case object ArgonautJsonMeta {

  def encodeExpr[T: c.WeakTypeTag](c: blackbox.Context)(value: c.Expr[T]): c.Expr[Json] = {
    import c.universe.Quasiquote

    c.Expr[Json](q"""
      import argonaut.Argonaut.ToJsonIdentity
      import automorph.codec.json.ArgonautJsonCodec.*
      $value.asJson
    """)
  }

  def decodeExpr[T: c.WeakTypeTag](c: blackbox.Context)(node: c.Expr[Json]): c.Expr[T] = {
    import c.universe.{weakTypeOf, Quasiquote}

    c.Expr[T](q"""
      import argonaut.Argonaut.ToJsonIdentity
      import automorph.codec.json.ArgonautJsonCodec.*
      $node.as[${weakTypeOf[T]}].fold(
        (errorMessage, _) => throw new IllegalArgumentException(errorMessage),
        identity
      )
    """)
  }
}
