package test.core

import automorph.{RpcClient, RpcEndpoint, RpcServer}
import automorph.codec.json.Json4sNativeJsonCodec
import automorph.protocol.JsonRpcProtocol
import org.json4s.{CustomSerializer, JInt}
import test.api.Enum
import test.core.Fixtures.{Apis, Fixture, Functions}

trait PlatformClientServerTest extends BaseClientServerTest {
  override def fixtures: Seq[TestFixture] =
    super.fixtures

  private def json4sNativeJsonRpcJsonFixture(implicit context: Context): TestFixture = {
      val formats = Json4sNativeJsonCodec.formats + new CustomSerializer[Enum.Enum](_ => ({
        case JInt(value) => Enum.fromOrdinal(value.toInt)
      }, {
        case value: Enum.Enum => JInt(Enum.toOrdinal(value))
      }))
      val codec = Json4sNativeJsonCodec(formats)
      val protocol = JsonRpcProtocol[Json4sNativeJsonCodec.Node, codec.type, Context](codec)
      RpcEndpoint.transport(endpointTransport).rpcProtocol(protocol).bind(simpleApi, mapName).bind(complexApi)
      val id = fixtureId(protocol, Some("JSON"))
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
