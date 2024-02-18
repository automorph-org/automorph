package automorph.codec.json.meta

import automorph.codec.json.UPickleJsonCodec.JsonConfig
import automorph.spi.MessageCodec
import ujson.Value
import scala.compiletime.summonInline

/**
 * uPickle JSON codec plugin code generation.
 *
 * @tparam Config
 *   uPickle configuration type
 */
private[automorph] trait UPickleJsonMeta[Config <: JsonConfig] extends MessageCodec[Value]:

  val config: Config

  override inline def encode[T](value: T): Value =
    config.writeJs(value)(using summonInline[config.Writer[T]])

  override inline def decode[T](node: Value): T =
    config.read[T](node)(using summonInline[config.Reader[T]])
