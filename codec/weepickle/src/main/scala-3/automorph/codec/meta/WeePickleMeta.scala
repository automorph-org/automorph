package automorph.codec.meta

import automorph.codec.WeePickleCodec
import automorph.spi.MessageCodec
import com.rallyhealth.weejson.v1.Value
import com.rallyhealth.weepickle.v1.WeePickle.{From, FromScala, To, ToScala}
import scala.compiletime.summonInline

/** weePickle codec plugin code generation. */
private[automorph] trait WeePickleMeta extends MessageCodec[Value]:

  @scala.annotation.nowarn("msg=unused import")
  override inline def encode[T](value: T): Value =
    import WeePickleCodec.given
    FromScala(value)(using summonInline[From[T]]).transform(Value)

  @scala.annotation.nowarn("msg=unused import")
  override inline def decode[T](node: Value): T =
    import WeePickleCodec.given
    node.transform(ToScala(using summonInline[To[T]]))
