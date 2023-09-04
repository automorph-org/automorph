package automorph.codec.meta

import automorph.codec.WeepickleCodec
import automorph.spi.MessageCodec
import com.rallyhealth.weejson.v1.Value
import com.rallyhealth.weepickle.v1.WeePickle.{From, FromScala, To, ToScala}
import scala.compiletime.summonInline

/** weePickle codec plugin code generation. */
private[automorph] trait WeepickleMeta extends MessageCodec[Value]:

  override inline def encode[T](value: T): Value =
    import WeepickleCodec.given
    FromScala(value)(using summonInline[From[T]]).transform(Value)

  override inline def decode[T](node: Value): T =
    import WeepickleCodec.given
    node.transform(ToScala(using summonInline[To[T]]))
