package test.core

import automorph.codec.UPickleJsonCodec.JsonConfig
import automorph.codec.UPickleMessagePackCodec.MessagePackConfig
import automorph.codec.{CirceJsonCodec, UPickleJsonCodec, UPickleMessagePackCodec}
import automorph.protocol.JsonRpcProtocol
import automorph.schema.OpenApi
import automorph.spi.{ClientTransport, ServerTransport, RpcProtocol}
import automorph.{RpcClient, RpcServer}
import io.circe.generic.auto.*
import io.circe.{Decoder, Encoder}
import test.api.{Enum, Record, Structure, TestLevel}
import test.core.Fixtures.{Apis, Fixture, Functions}

trait ProtocolCodecTest extends CoreTest with ProtocolCodecTestPlatformSpecific {

  type OptionalServer = Option[RpcServer[?, ?, Effect, Context, ?]]

  override def fixtures: Seq[TestFixture] = {
    implicit val context: Context = arbitraryContext.arbitrary.sample.get
    Seq(circeJsonRpcFixture) ++ Option.when((mandatory && !TestLevel.simple) || TestLevel.all)(Seq(
      uPickleJsonRpcJsonFixture,
      uPickleJsonRpcMessagePackFixture,
    ) ++ platformSpecificFixtures).getOrElse(Seq())
  }

  def clientTransport(fixtureId: String, server: OptionalServer): ClientTransport[Effect, ?]

  def serverTransport(fixtureId: String): ServerTransport[Effect, Context, Unit]

  def typedClientTransport(fixtureId: String, server: OptionalServer): ClientTransport[Effect, Context] =
    clientTransport(fixtureId, server).asInstanceOf[ClientTransport[Effect, Context]]

  def mapName(name: String): Seq[String] =
    name match {
      case "method" => Seq("method", "function")
      case name => Seq(name)
    }

  def fixtureId(rpcProtocol: RpcProtocol[?, ?, ?], format: Option[String] = None): String = {
    val codecName = rpcProtocol.messageCodec.getClass.getSimpleName.replaceAll("MessageCodec$", "")
    val suffix = format.map(" / " + _).getOrElse("")
    s"${rpcProtocol.name} / $codecName$suffix"
  }

  private def circeJsonRpcFixture(implicit context: Context): TestFixture = {
    implicit val enumEncoder: Encoder[Enum.Enum] = Encoder.encodeInt.contramap[Enum.Enum](Enum.toOrdinal)
    implicit val enumDecoder: Decoder[Enum.Enum] = Decoder.decodeInt.map(Enum.fromOrdinal)
    val codec = CirceJsonCodec()
    val protocol = JsonRpcProtocol[CirceJsonCodec.Value, codec.type, Context](codec)
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



  private def uPickleJsonRpcJsonFixture(implicit context: Context): TestFixture = {
    final class CustomConfig extends JsonConfig {
      implicit lazy val enumRw: ReadWriter[Enum.Enum] = readwriter[Int]
        .bimap[Enum.Enum](value => Enum.toOrdinal(value), number => Enum.fromOrdinal(number))
      implicit lazy val structureRw: ReadWriter[Structure] = macroRW
      implicit lazy val recordRw: ReadWriter[Record] = macroRW
    }
    val customConfig = new CustomConfig
    val codec = UPickleJsonCodec(customConfig)
    val protocol = JsonRpcProtocol[UPickleJsonCodec.Value, codec.type, Context](codec)
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

  private def uPickleJsonRpcMessagePackFixture(implicit context: Context): TestFixture = {
    final class CustomConfig extends MessagePackConfig {
      implicit lazy val enumRw: ReadWriter[Enum.Enum] = readwriter[Int]
        .bimap[Enum.Enum](value => Enum.toOrdinal(value), number => Enum.fromOrdinal(number))
      implicit lazy val structureRw: ReadWriter[Structure] = macroRW
      implicit lazy val recordRw: ReadWriter[Record] = macroRW
    }
    val customConfig = new CustomConfig
    val codec = UPickleMessagePackCodec(customConfig)
    val protocol = JsonRpcProtocol[UPickleMessagePackCodec.Value, codec.type, Context](codec)
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
        (f, a0) => client.call[String](f)(a0),
        (f, a0) => client.tell(f)(a0),
      ),
    )
  }

}
