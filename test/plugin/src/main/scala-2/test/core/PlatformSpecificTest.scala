package test.core

import automorph.{RpcClient, RpcEndpoint, RpcServer}
import automorph.codec.json.Json4sNativeJsonCodec
import automorph.codec.json.meta.PlayJsonCodec
import automorph.protocol.JsonRpcProtocol
import org.json4s.{CustomSerializer, JInt}
import play.api.libs.json.{JsNumber, JsValue, Json, Reads, Writes}
import test.api.{Enum, Record, Structure, TestLevel}
import test.core.Fixtures.{Apis, Fixture, Functions}

trait PlatformSpecificTest extends ProtocolCodecTest {

  override def fixtures: Seq[TestFixture] = {
    implicit val context: Context = arbitraryContext.arbitrary.sample.get
    super.fixtures ++ Option.when(basic || TestLevel.all)(Seq(
      playJsonJsonRpcFixture,
      json4sNativeJsonRpcFixture,
    )).getOrElse(Seq())
  }

  @scala.annotation.nowarn("msg=never used")
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
    val protocol = JsonRpcProtocol[PlayJsonCodec.Node, codec.type, Context](codec)
    RpcEndpoint.transport(endpointTransport).rpcProtocol(protocol).bind(simpleApi, mapName).bind(complexApi)
    val id = fixtureId(protocol)
    val server = RpcServer.transport(serverTransport(id)).rpcProtocol(protocol).discovery(true)
      .bind(simpleApi, mapName).bind(complexApi)
    val client = RpcClient.transport(typedClientTransport(id)).rpcProtocol(protocol)
    Fixture(
      id, client, server,
      Apis(client.bind[SimpleApiType], client.bind[ComplexApiType], client.bind[InvalidApiType]),
      Functions(f => client.call(f)(), (f, a0) => client.call(f)(a0), (f, a0) => client.tell(f)(a0)),
    )
  }

  private def json4sNativeJsonRpcFixture(implicit context: Context): TestFixture = {
    val formats = Json4sNativeJsonCodec.formats + new CustomSerializer[Enum.Enum](_ => ({
      case JInt(value) => Enum.fromOrdinal(value.toInt)
    }, {
      case value: Enum.Enum => JInt(Enum.toOrdinal(value))
    }))
    val codec = Json4sNativeJsonCodec(formats)
    val protocol = JsonRpcProtocol[Json4sNativeJsonCodec.Node, codec.type, Context](codec)
    RpcEndpoint.transport(endpointTransport).rpcProtocol(protocol).bind(simpleApi, mapName).bind(complexApi)
    val id = fixtureId(protocol)
    val server = RpcServer.transport(serverTransport(id)).rpcProtocol(protocol).discovery(true)
      .bind(simpleApi, mapName).bind(complexApi)
    val client = RpcClient.transport(typedClientTransport(id)).rpcProtocol(protocol)
    Fixture(
      id, client, server,
      Apis(client.bind[SimpleApiType], client.bind[ComplexApiType], client.bind[InvalidApiType]),
      Functions(f => client.call(f)(), (f, a0) => client.call(f)(a0), (f, a0) => client.tell(f)(a0)),
    )
  }
}
