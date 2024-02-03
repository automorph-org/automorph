package automorph.codec.json.meta

import automorph.codec.json.ArgonautJsonCodec
import argonaut.Argonaut.ToJsonIdentity
import argonaut.{DecodeJson, EncodeJson, Json}
import automorph.spi.MessageCodec
import scala.compiletime.summonInline

/** Argonaut JSON codec plugin code generation. */
private[automorph] trait ArgonautJsonMeta extends MessageCodec[Json]:

  @scala.annotation.nowarn("msg=unused import")
  override inline def encode[T](value: T): Json =
    import ArgonautJsonCodec.given
    value.asJson(using summonInline[EncodeJson[T]])

  @scala.annotation.nowarn("msg=unused import")
  override inline def decode[T](node: Json): T =
    import ArgonautJsonCodec.given
    node.as[T](using summonInline[DecodeJson[T]])
      .fold((errorMessage, _) => throw new IllegalArgumentException(errorMessage), identity)
