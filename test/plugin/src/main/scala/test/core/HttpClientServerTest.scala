package test.core

import automorph.{RpcClient, RpcEndpoint, RpcServer}
import automorph.codec.CirceJsonCodec
import automorph.protocol.WebRpcProtocol
import automorph.transport.HttpContext
import io.circe.generic.auto.*
import io.circe.{Decoder, Encoder}
import test.api.Enum
import test.core.Fixtures.{Apis, Fixture, Functions}

trait HttpClientServerTest extends ClientServerTest {
  type Context <: HttpContext[?]

  override def fixtures: Seq[TestFixture] = {
    implicit val context: Context = arbitraryContext.arbitrary.sample.get
    Seq(circeWebRpcJsonFixture()) ++ super.fixtures
  }

  @scala.annotation.nowarn("msg=never used")
  private def circeWebRpcJsonFixture()(implicit context: Context): TestFixture = {
    implicit val enumEncoder: Encoder[Enum.Enum] = Encoder.encodeInt.contramap[Enum.Enum](Enum.toOrdinal)
    implicit val enumDecoder: Decoder[Enum.Enum] = Decoder.decodeInt.map(Enum.fromOrdinal)
    val codec = CirceJsonCodec()
    val protocol = WebRpcProtocol[CirceJsonCodec.Node, codec.type, Context](codec, "/")
    RpcEndpoint.transport(endpointTransport).rpcProtocol(protocol).bind(simpleApi, mapName).bind(complexApi)
    val id = fixtureId(protocol)
    val server = RpcServer.transport(serverTransport(id)).rpcProtocol(protocol).discovery(true)
      .bind(simpleApi, mapName).bind(complexApi)
    val client = RpcClient.transport(typedClientTransport(id)).rpcProtocol(protocol)
    Fixture(
      id,
      client,
      server,
      Apis(client.bind[SimpleApiType], client.bind[ComplexApiType], client.bind[InvalidApiType]),
      Functions(f => client.call(f)(), (f, a0) => client.call(f)(a0), (f, a0) => client.tell(f)(a0)),
    )
  }
}
