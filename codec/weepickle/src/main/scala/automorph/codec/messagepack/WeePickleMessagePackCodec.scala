package automorph.codec.messagepack

import automorph.codec.WeePickleCodec.NullSafeTo
import automorph.codec.messagepack.meta.WeePickleMessagePackMeta
import automorph.schema.{OpenApi, OpenRpc}
import com.rallyhealth.weepack.v1.{FromMsgPack, Msg, ToMsgPack}
import com.rallyhealth.weepickle.v1.WeePickle
import com.rallyhealth.weepickle.v1.WeePickle.{FromTo, To}
import java.util.Base64
import scala.collection.compat.Factory
import scala.reflect.ClassTag

/**
 * weePickle MessagePack message codec plugin.
 *
 * @see
 *   [[https://msgpack.org Message format]]
 * @see
 *   [[https://github.com/rallyhealth/weePickle Library documentation]]
 * @see
 *   [[https://javadoc.io/doc/com.rallyhealth/weepack-v1_3/latest/com/rallyhealth/weepack/v1/Msg.html Node type]]
 * @constructor
 *   Creates a weePickle codec plugin using MessagePack as message format.
 */
final case class WeePickleMessagePackCodec() extends WeePickleMessagePackMeta {
  override val mediaType: String = "application/msgpack"

  override def serialize(node: Msg): Array[Byte] =
    node.transform(ToMsgPack.bytes)

  override def deserialize(data: Array[Byte]): Msg =
    FromMsgPack(data).transform(Msg)

  override def text(node: Msg): String =
    Base64.getEncoder.encodeToString(node.transform(ToMsgPack.bytes))
}

object WeePickleMessagePackCodec {

  // Do not deserialize nulls as empty collections
  implicit def ToSeqLike[C[_], T](implicit r: To[T], factory: Factory[T, C[T]]): To[C[T]] =
    new NullSafeTo[C[T]](WeePickle.ToSeqLike, "sequence")

  implicit def ToArray[T: To: ClassTag]: To[Array[T]] =
    new NullSafeTo[Array[T]](WeePickle.ToArray, "sequence")

  implicit def ToMap[K, V](implicit k: To[K], v: To[V]): To[collection.Map[K, V]] =
    new NullSafeTo[collection.Map[K, V]](WeePickle.ToMap, "map")

  /** Do not deserialize null as empty map. */
  implicit def ToImmutableMap[K, V](implicit k: To[K], v: To[V]): To[collection.immutable.Map[K, V]] =
    new NullSafeTo[collection.immutable.Map[K, V]](WeePickle.ToImmutableMap, "map")

  implicit def ToMutableMap[K, V](implicit k: To[K], v: To[V]): To[collection.mutable.Map[K, V]] =
    new NullSafeTo[collection.mutable.Map[K, V]](WeePickle.ToMutableMap, "map")

  /** Message node type. */
  type Node = Msg
  implicit lazy val jsonRpcFromTo: FromTo[WeePickleJsonRpc.RpcMessage] = WeePickleJsonRpc.fromTo
  implicit lazy val webRpcFromTo: FromTo[WeePickleWebRpc.RpcMessage] = WeePickleWebRpc.fromTo
  implicit lazy val openRpcFromTo: FromTo[OpenRpc] = WeePickleOpenRpc.fromTo
  implicit lazy val openApiFromTo: FromTo[OpenApi] = WeePickleOpenApi.fromTo
}
