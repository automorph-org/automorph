package test.core

import automorph.{RpcClient, RpcEndpoint, RpcServer}
import automorph.codec.json.CirceJsonCodec
import automorph.protocol.WebRpcProtocol
import automorph.transport.http.HttpContext
import io.circe.generic.auto.*
import io.circe.{Decoder, Encoder}
import test.api.Enum
import test.base.BaseTest

trait HttpProtocolCodecTest extends ProtocolCodecTest {
  type Context <: HttpContext[?]

  override def createFixtures(implicit context: Context): Seq[TestFixture] =
    super.createFixtures ++ {
      if (BaseTest.testSimple || BaseTest.testAll || !integration) {
        Seq(circeWebRpcJsonFixture(5))
      } else {
        Seq.empty
      }
    }

  private def circeWebRpcJsonFixture(id: Int)(implicit context: Context): TestFixture = {
    implicit val enumEncoder: Encoder[Enum.Enum] = Encoder.encodeInt.contramap[Enum.Enum](Enum.toOrdinal)
    implicit val enumDecoder: Decoder[Enum.Enum] = Decoder.decodeInt.map(Enum.fromOrdinal)
    Seq(enumEncoder, enumDecoder)
    val codec = CirceJsonCodec()
    val protocol = WebRpcProtocol[CirceJsonCodec.Node, codec.type, Context](codec, "/")
    RpcEndpoint.transport(endpointTransport).rpcProtocol(protocol).bind(simpleApi, mapName).bind(complexApi)
    val server =
      RpcServer.transport(serverTransport(id)).rpcProtocol(protocol).bind(simpleApi, mapName).bind(complexApi)
    val client = RpcClient.transport(typedClientTransport(id)).rpcProtocol(protocol)
    TestFixture(
      client,
      server,
      client.bind[SimpleApiType],
      client.bind[ComplexApiType],
      client.bind[InvalidApiType],
      (function, a0) => client.call[String](function)(a0),
      (function, a0) => client.tell(function)(a0),
    )
  }
}
