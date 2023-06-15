package automorph.codec.json.meta

import automorph.codec.json.UpickleJsonCustom
import automorph.spi.MessageCodec
import scala.compiletime.summonInline
import ujson.Value

/**
 * UPickle JSON codec plugin code generation.
 *
 * @tparam Custom
 *   customized Upickle reader and writer implicits instance type
 */
private[automorph] trait UpickleJsonMeta[Custom <: UpickleJsonCustom] extends MessageCodec[Value]:

  val custom: Custom

  override inline def encode[T](value: T): Value =
    custom.writeJs(value)(using summonInline[custom.Writer[T]])

  override inline def decode[T](node: Value): T =
    custom.read[T](node)(using summonInline[custom.Reader[T]])
