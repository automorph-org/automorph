package automorph.codec.json.meta

import automorph.spi.MessageCodec
import io.circe.Json
import scala.language.experimental.macros
import scala.reflect.macros.blackbox

/** Circe JSON codec plugin code generation. */
private[automorph] trait CirceJsonMeta extends MessageCodec[Json] {

  override def encode[T](value: T): Json =
    macro CirceJsonMeta.encodeMacro[T]

  override def decode[T](node: Json): T =
    macro CirceJsonMeta.decodeMacro[T]
}

private[automorph] object CirceJsonMeta {

  def encodeMacro[T: c.WeakTypeTag](c: blackbox.Context)(value: c.Expr[T]): c.Expr[Json] = {
    import c.universe.Quasiquote

    c.Expr[Json](q"""
      import io.circe.syntax.EncoderOps
      import automorph.codec.json.CirceJsonCodec.*
      $value.asJson
    """)
  }

  def decodeMacro[T: c.WeakTypeTag](c: blackbox.Context)(node: c.Expr[Json]): c.Expr[T] = {
    import c.universe.{Quasiquote, weakTypeOf}

    c.Expr[T](q"""
      import automorph.codec.json.CirceJsonCodec.*
      $node.as[${weakTypeOf[T]}].toTry.get
    """)
  }
}
