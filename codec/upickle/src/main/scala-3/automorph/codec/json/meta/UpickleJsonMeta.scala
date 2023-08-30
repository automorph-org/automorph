package automorph.codec.json.meta

import automorph.codec.json.UpickleJsonConfig
import automorph.spi.MessageCodec
import scala.compiletime.summonInline
import ujson.Value

/**
 * uPickle JSON codec plugin code generation.
 *
 * @tparam Config
 *   uPickle configuration type
 */
private[automorph] trait UpickleJsonMeta[Config <: UpickleJsonConfig] extends MessageCodec[Value]:

  val config: Config

  override inline def encode[T](value: T): Value =
    config.writeJs(value)(using summonInline[config.Writer[T]])

  override inline def decode[T](node: Value): T =
    config.read[T](node)(using summonInline[config.Reader[T]])
