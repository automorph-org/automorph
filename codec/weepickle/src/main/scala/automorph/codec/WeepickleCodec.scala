package automorph.codec

import automorph.codec.meta.WeepickleMeta
import automorph.schema.{OpenApi, OpenRpc}
import com.fasterxml.jackson.core.{JsonFactory, JsonGenerator}
import com.fasterxml.jackson.dataformat.cbor.{CBORFactory, CBORFactoryBuilder}
import com.fasterxml.jackson.dataformat.ion.{IonFactory, IonFactoryBuilder}
import com.fasterxml.jackson.dataformat.smile.{SmileFactory, SmileFactoryBuilder}
import com.rallyhealth.weejson.v1.Value
import com.rallyhealth.weejson.v1.jackson.{CustomPrettyPrinter, DefaultJsonFactory, JsonGeneratorOps, JsonParserOps}
import com.rallyhealth.weepickle.v1.WeePickle.{FromTo, SimpleTo, To}
import com.rallyhealth.weepickle.v1.WeePickle
import com.rallyhealth.weepickle.v1.core.{Abort, ArrVisitor, ObjVisitor}
import java.time.Instant
import java.util.Base64
import scala.collection.compat.Factory
import scala.reflect.ClassTag

/**
 * weePickle message codec plugin.
 *
 * Specific message format depends on the supplied format factory with the following options:
 *   - JSON (default) - WeepickleCodec.jsonFactory
 *   - CBOR - WeepickleCodec.cborFactory
 *   - Smile - WeepickleCodec.smileFactory
 *
 * @see
 *   [[https://www.json.org JSON message format]]
 * @see
 *   [[https://github.com/FasterXML/smile-format-specification Smile message format]]
 * @see
 *   [[https://cbor.io CBOR message format]]
 * @see
 *   [[https://amazon-ion.github.io/ion-docs Ion message format]]
 * @see
 *   [[https://github.com/rallyhealth/weePickle Library documentation]]
 * @see
 *   [[https://javadoc.io/doc/com.rallyhealth/weejson-v1_3/latest/com/rallyhealth/weejson/v1/Value.html Node type]]
 * @constructor
 *   Creates an weePickle codec plugin using specific message format.
 * @param formatFactory
 *   Jackson data format factory
 */
final case class WeepickleCodec(formatFactory: JsonFactory = WeepickleCodec.jsonFactory) extends WeepickleMeta {
  private val jsonGenerator = new JsonGeneratorOps(formatFactory) {}
  private val jsonTextGenerator = new JsonGeneratorOps(formatFactory) {

    override protected def wrapGenerator(g: JsonGenerator): JsonGenerator =
      g.setPrettyPrinter(CustomPrettyPrinter(2))
  }
  private val jsonParser = new JsonParserOps(formatFactory) {}

  override val mediaType: String = formatFactory match {
    case _: SmileFactory => "application/x-jackson-smile"
    case _: CBORFactory => "application/cbor"
    case _: IonFactory => "application/ion"
    case _ => "application/json"
  }

  override def serialize(node: Value): Array[Byte] =
    node.transform(jsonGenerator.bytes)

  override def deserialize(data: Array[Byte]): Value =
    jsonParser(data).transform(Value)

  override def text(node: Value): String =
    formatFactory match {
      case _: SmileFactory | _: CBORFactory | _: IonFactory =>
        Base64.getEncoder.encodeToString(node.transform(jsonGenerator.bytes))
      case _ => node.transform(jsonTextGenerator.string)
    }
}

object WeepickleCodec {

  /** Message node type. */
  type Node = Value

  implicit lazy val jsonRpcFromTo: FromTo[WeepickleJsonRpc.RpcMessage] = WeepickleJsonRpc.fromTo
  implicit lazy val webRpcFromTo: FromTo[WeepickleWebRpc.RpcMessage] = WeepickleWebRpc.fromTo
  implicit lazy val openRpcFromTo: FromTo[OpenRpc] = WeepickleOpenRpc.fromTo
  implicit lazy val openApiFromTo: FromTo[OpenApi] = WeepickleOpenApi.fromTo

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

  /** Default Jackson JSON factory. */
  def jsonFactory: JsonFactory =
    DefaultJsonFactory.Instance

  /** Default Jackson Smile factory. */
  def smileFactory: JsonFactory =
    new SmileFactoryBuilder(new SmileFactory).configure(JsonFactory.Feature.INTERN_FIELD_NAMES, false).build()

  /** Default Jackson CBOR factory. */
  def cborFactory: JsonFactory =
    new CBORFactoryBuilder(new CBORFactory).configure(JsonFactory.Feature.INTERN_FIELD_NAMES, false).build()

  /** Default Jackson Ion factory. */
  def ionFactory: JsonFactory =
    new IonFactoryBuilder(new IonFactory).configure(JsonFactory.Feature.INTERN_FIELD_NAMES, false).build()

  private class NullSafeTo[T](to: To[T], typeName: String) extends SimpleTo[T] {

    override def visitArray(length: Int): ArrVisitor[Any, T] =
      to.visitArray(length)

    override def visitObject(length: Int): ObjVisitor[Any, T] =
      to.visitObject(length)

    override def visitNull(): T =
      throw new Abort(s"$expectedMsg got null")

    override def visitFalse(): T =
      to.visitFalse()

    override def visitTrue(): T =
      to.visitTrue()

    override def visitFloat64StringParts(cs: CharSequence, decIndex: Int, expIndex: Int): T =
      to.visitFloat64StringParts(cs, decIndex, expIndex)

    override def visitFloat64(d: Double): T =
      to.visitFloat64(d)

    override def visitFloat32(d: Float): T =
      to.visitFloat32(d)

    override def visitInt32(i: Int): T =
      to.visitInt32(i)

    override def visitInt64(l: Long): T =
      to.visitInt64(l)

    override def visitUInt64(ul: Long): T =
      to.visitUInt64(ul)

    override def visitFloat64String(s: String): T =
      to.visitFloat64String(s)

    override def visitString(cs: CharSequence): T =
      to.visitString(cs)

    override def visitChar(c: Char): T =
      to.visitChar(c)

    override def visitBinary(bytes: Array[Byte], offset: Int, len: Int): T =
      to.visitBinary(bytes, offset, len)

    override def visitExt(tag: Byte, bytes: Array[Byte], offset: Int, len: Int): T =
      to.visitExt(tag, bytes, offset, len)

    override def visitTimestamp(instant: Instant): T =
      to.visitTimestamp(instant)

    override def expectedMsg: String =
      s"expected $typeName"
  }
}
