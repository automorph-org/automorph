package automorph.codec.messagepack.meta

import automorph.codec.messagepack.UPickleMessagePackConfig
import automorph.spi.MessageCodec
import scala.compiletime.summonInline
import upack.Msg

/**
 * uPickle JSON codec plugin code generation.
 *
 * @tparam Config
 *   uPickle configuration type
 */
private[automorph] trait UPickleMessagePackMeta[Config <: UPickleMessagePackConfig] extends MessageCodec[Msg]:

  val config: Config

  override inline def encode[T](value: T): Msg =
    config.writeMsg(value)(using summonInline[config.Writer[T]])

  override inline def decode[T](node: Msg): T =
    config.readBinary[T](node)(using summonInline[config.Reader[T]])
