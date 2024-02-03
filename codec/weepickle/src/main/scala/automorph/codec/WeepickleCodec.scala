package automorph.codec

import automorph.codec.meta.WeepickleMeta
import automorph.schema.{OpenApi, OpenRpc}
import com.fasterxml.jackson.core.{JsonFactory, JsonGenerator}
import com.fasterxml.jackson.dataformat.cbor.{CBORFactory, CBORFactoryBuilder}
import com.fasterxml.jackson.dataformat.smile.{SmileFactory, SmileFactoryBuilder}
import com.rallyhealth.weejson.v1.Value
import com.rallyhealth.weejson.v1.jackson.{CustomPrettyPrinter, DefaultJsonFactory, JsonGeneratorOps, JsonParserOps}
import com.rallyhealth.weepickle.v1.WeePickle.FromTo
import java.util.Base64

/**
 * weePickle message codec plugin.
 *
 * Specific message format depends on the supplied format factory with the following options:
 * - JSON (default) - WeepickleCodec.jsonFactory
 * - CBOR - WeepickleCodec.cborFactory
 * - Smile - WeepickleCodec.smileFactory
 *
 * @see
 *   [[https://www.json.org JSON message format]]
 * @see
 *   [[https://cbor.io CBOR message format]]
 * @see
 *   [[https://github.com/FasterXML/smile-format-specification Smile message format]]
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
    case _: CBORFactory => "application/cbor"
    case _: SmileFactory => "application/x-jackson-smile"
    case _ => "application/json"
  }

  override def serialize(node: Value): Array[Byte] =
    node.transform(jsonGenerator.bytes)

  override def deserialize(data: Array[Byte]): Value =
    jsonParser(data).transform(Value)

  override def text(node: Value): String =
    formatFactory match {
      case _: CBORFactory | _: SmileFactory => Base64.getEncoder.encodeToString(node.transform(jsonGenerator.bytes))
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

  /** Default Jackson JSON factory. */
  def jsonFactory: JsonFactory =
    DefaultJsonFactory.Instance

  /** Default Jackson CBOR factory. */
  def cborFactory: JsonFactory =
    new CBORFactoryBuilder(new CBORFactory).configure(JsonFactory.Feature.INTERN_FIELD_NAMES, false).build()

  /** Default Jackson Smile factory. */
  def smileFactory: JsonFactory =
    new SmileFactoryBuilder(new SmileFactory).configure(JsonFactory.Feature.INTERN_FIELD_NAMES, false).build()
}
