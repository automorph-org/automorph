package automorph.codec.meta

import automorph.spi.MessageCodec
import automorph.codec.WeePickleMessagePackCodec
import com.rallyhealth.weepack.v1.Msg
import com.rallyhealth.weepickle.v1.WeePickle.{From, FromScala, To, ToScala}
import scala.compiletime.summonInline

/** weePickle MessagePack codec plugin code generation. */
trait WeePickleMessagePackMeta extends MessageCodec[Msg]:

  @scala.annotation.nowarn("msg=unused import")
  override inline def encode[T](value: T): Msg =
    import WeePickleMessagePackCodec.given
    FromScala(value)(using summonInline[From[T]]).transform(Msg)

  @scala.annotation.nowarn("msg=unused import")
  override inline def decode[T](node: Msg): T =
    import WeePickleMessagePackCodec.given
    node.transform(ToScala(using summonInline[To[T]]))
