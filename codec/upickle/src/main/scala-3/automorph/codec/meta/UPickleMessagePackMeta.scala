package automorph.codec.meta

import automorph.codec.UPickleMessagePackCodec.MessagePackConfig
import automorph.spi.MessageCodec
import upack.Msg
import scala.compiletime.summonInline

/**
 * uPickle JSON codec plugin code generation.
 *
 * @tparam Config
 *   uPickle configuration type
 */
private[automorph] trait UPickleMessagePackMeta[Config <: MessagePackConfig] extends MessageCodec[Msg]:

  val config: Config

  override inline def encode[T](value: T): Msg =
    config.writeMsg(value)(using summonInline[config.Writer[T]])

  override inline def decode[T](node: Msg): T =
    config.readBinary[T](node)(using summonInline[config.Reader[T]])
