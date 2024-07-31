package test.core

import automorph.codec.{Json4sNativeJsonCodec, PlayJsonCodec}
import automorph.protocol.JsonRpcProtocol
import automorph.{RpcClient, RpcServer}
import org.json4s.{CustomSerializer, JInt}
import play.api.libs.json.{JsNumber, JsValue, Json, Reads, Writes}
import test.api.{Enum, Record, Structure, TestLevel}
import test.core.Fixtures.{Apis, Fixture, Functions}

trait PlatformSpecificTest extends ProtocolCodecTest {

  override def fixtures: Seq[TestFixture] = {
    implicit val context: Context = arbitraryContext.arbitrary.sample.get
    super.fixtures ++ Option.when(mandatory || TestLevel.all)(Seq(
      playJsonJsonRpcFixture,
      json4sNativeJsonRpcFixture,
    )).getOrElse(Seq())
  }

  private def playJsonJsonRpcFixture(implicit context: Context): TestFixture = {
    implicit val recordReads: Reads[Record] = {
      implicit val enumReads: Reads[Enum.Enum] = (json: JsValue) => json.validate[Int].map(Enum.fromOrdinal)
      implicit val structureReads: Reads[Structure] = Json.reads
      Json.reads
    }
    implicit val recordWrites: Writes[Record] = {
      implicit val enumWrites: Writes[Enum.Enum] = (value: Enum.Enum) => JsNumber(BigDecimal(Enum.toOrdinal(value)))
      implicit val structureWrites: Writes[Structure] = Json.writes
      Json.writes
    }
    val codec = PlayJsonCodec()
    val protocol = JsonRpcProtocol[PlayJsonCodec.Value, codec.type, Context](codec)
    val id = fixtureId(protocol)
    val server = RpcServer.transport(serverTransport(id)).rpcProtocol(protocol).discovery(true)
      .service(simpleApi, mapName).service(complexApi)
    val client = RpcClient.transport(typedClientTransport(id, Some(server))).rpcProtocol(protocol)
    Fixture(
      id,
      client,
      server,
      Apis(client.proxy[SimpleApiType], client.proxy[ComplexApiType], client.proxy[InvalidApiType]),
      Functions(
        f => client.call(f)(),
        f => client.call(f)(),
        (f, a0) => client.call(f)(a0),
        (f, a0) => client.tell(f)(a0),
      ),
    )
  }

  private def json4sNativeJsonRpcFixture(implicit context: Context): TestFixture = {
    val formats = Json4sNativeJsonCodec.formats + new CustomSerializer[Enum.Enum](_ =>
      (
        {
          case JInt(enumValue) => Enum.fromOrdinal(enumValue.toInt)
        },
        {
          case enumValue: Enum.Enum => JInt(Enum.toOrdinal(enumValue))
        },
      )
    )
    val codec = Json4sNativeJsonCodec(formats)
    val protocol = JsonRpcProtocol[Json4sNativeJsonCodec.Value, codec.type, Context](codec)
    val id = fixtureId(protocol)
    val server = RpcServer.transport(serverTransport(id)).rpcProtocol(protocol).discovery(true)
      .service(simpleApi, mapName).service(complexApi)
    val client = RpcClient.transport(typedClientTransport(id, Some(server))).rpcProtocol(protocol)
    Fixture(
      id,
      client,
      server,
      Apis(client.proxy[SimpleApiType], client.proxy[ComplexApiType], client.proxy[InvalidApiType]),
      Functions(
        f => client.call(f)(),
        f => client.call(f)(),
        (f, a0) => client.call(f)(a0),
        (f, a0) => client.tell(f)(a0),
      ),
    )
  }
}
