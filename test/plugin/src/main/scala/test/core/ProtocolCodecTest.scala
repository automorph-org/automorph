package test.core

import automorph.codec.json.UPickleJsonCodec.JsonConfig
import automorph.codec.json.{CirceJsonCodec, UPickleJsonCodec}
import automorph.codec.messagepack.UPickleMessagePackCodec.MessagePackConfig
import automorph.codec.messagepack.{UPickleMessagePackCodec, WeePickleMessagePackCodec}
import automorph.codec.{JacksonCodec, WeePickleCodec}
import automorph.protocol.JsonRpcProtocol
import automorph.schema.OpenApi
import automorph.spi.{ClientTransport, EndpointTransport, RpcProtocol, ServerTransport}
import automorph.transport.generic.endpoint.GenericEndpoint
import automorph.{RpcClient, RpcEndpoint, RpcServer}
import com.fasterxml.jackson.core.{JsonGenerator, JsonParser}
import com.fasterxml.jackson.databind.deser.std.StdDeserializer
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.databind.ser.std.StdSerializer
import com.fasterxml.jackson.databind.{DeserializationContext, SerializerProvider}
import com.rallyhealth.weepickle.v1.WeePickle.{FromInt, FromTo, ToInt, macroFromTo}
import io.circe.generic.auto.*
import io.circe.{Decoder, Encoder}
import test.api.{Enum, Record, Structure, TestLevel}
import test.core.Fixtures.{Apis, Fixture, Functions}

trait ProtocolCodecTest extends CoreTest {

  @scala.annotation.nowarn("msg=never used")
  implicit private lazy val recordFromTo: FromTo[Record] = {
    implicit val enumFromTo: FromTo[Enum.Enum] = FromTo.join(ToInt, FromInt).bimap(Enum.toOrdinal, Enum.fromOrdinal)
    implicit val structureFromTo: FromTo[Structure] = macroFromTo
    macroFromTo
  }

  override def fixtures: Seq[TestFixture] = {
    implicit val context: Context = arbitraryContext.arbitrary.sample.get
    Seq(circeJsonRpcFixture) ++ Option.when(basic || TestLevel.all)(Seq(
      jacksonJsonRpcJsonFixture,
      jacksonJsonRpcSmileFixture,
      jacksonJsonRpcCborFixture,
      weePickleJsonRpcJsonFixture,
      weePickleJsonRpcSmileFixture,
      weePickleJsonRpcCborFixture,
      weePickleJsonRpcIonFixture,
      weePickleJsonRpcMessagePackFixture,
      uPickleJsonRpcJsonFixture,
      uPickleJsonRpcMessagePackFixture,
    )).getOrElse(Seq())
  }

  def clientTransport(fixtureId: String): ClientTransport[Effect, ?]

  def serverTransport(fixtureId: String): ServerTransport[Effect, Context]

  def endpointTransport: EndpointTransport[Effect, Context, ?] =
    GenericEndpoint(system).asInstanceOf[EndpointTransport[Effect, Context, ?]]

  def typedClientTransport(fixtureId: String): ClientTransport[Effect, Context] =
    clientTransport(fixtureId).asInstanceOf[ClientTransport[Effect, Context]]

  def mapName(name: String): Seq[String] =
    name match {
      case "method" => Seq("method", "function")
      case value => Seq(value)
    }

  def fixtureId(rpcProtocol: RpcProtocol[?, ?, ?], format: Option[String] = None): String = {
    val codecName = rpcProtocol.messageCodec.getClass.getSimpleName.replaceAll("MessageCodec$", "")
    val suffix = format.map(" / " + _).getOrElse("")
    s"${rpcProtocol.name} / $codecName$suffix"
  }

  @scala.annotation.nowarn("msg=never used")
  private def circeJsonRpcFixture(implicit context: Context): TestFixture = {
    implicit val enumEncoder: Encoder[Enum.Enum] = Encoder.encodeInt.contramap[Enum.Enum](Enum.toOrdinal)
    implicit val enumDecoder: Decoder[Enum.Enum] = Decoder.decodeInt.map(Enum.fromOrdinal)
    val codec = CirceJsonCodec()
    val protocol = JsonRpcProtocol[CirceJsonCodec.Node, codec.type, Context](codec)
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

  private def jacksonJsonRpcJsonFixture(implicit context: Context): TestFixture = {
    val codec = JacksonCodec(JacksonCodec.jsonMapper.registerModule(enumModule))
    val protocol = JsonRpcProtocol[JacksonCodec.Node, codec.type, Context](codec)
    RpcEndpoint.transport(endpointTransport).rpcProtocol(protocol).bind(simpleApi, mapName).bind(complexApi)
    val id = fixtureId(protocol, Some("JSON"))
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

  private def jacksonJsonRpcSmileFixture(implicit context: Context): TestFixture = {
    val codec = JacksonCodec(JacksonCodec.smileMapper.registerModule(enumModule))
    val protocol = JsonRpcProtocol[JacksonCodec.Node, codec.type, Context](codec)
    RpcEndpoint.transport(endpointTransport).rpcProtocol(protocol).bind(simpleApi, mapName).bind(complexApi)
    val id = fixtureId(protocol, Some("Smile"))
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

  private def jacksonJsonRpcCborFixture(implicit context: Context): TestFixture = {
    val codec = JacksonCodec(JacksonCodec.cborMapper.registerModule(enumModule))
    val protocol = JsonRpcProtocol[JacksonCodec.Node, codec.type, Context](codec)
    RpcEndpoint.transport(endpointTransport).rpcProtocol(protocol).bind(simpleApi, mapName).bind(complexApi)
    val id = fixtureId(protocol, Some("CBOR"))
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

  private def weePickleJsonRpcJsonFixture(implicit context: Context): TestFixture = {
    val codec = WeePickleCodec()
    val protocol = JsonRpcProtocol[WeePickleCodec.Node, codec.type, Context](codec)
    RpcEndpoint.transport(endpointTransport).rpcProtocol(protocol).bind(simpleApi, mapName).bind(complexApi)
    val id = fixtureId(protocol, Some("JSON"))
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

  private def weePickleJsonRpcSmileFixture(implicit context: Context): TestFixture = {
    val codec = WeePickleCodec(WeePickleCodec.smileFactory)
    val protocol = JsonRpcProtocol[WeePickleCodec.Node, codec.type, Context](codec)
    RpcEndpoint.transport(endpointTransport).rpcProtocol(protocol).bind(simpleApi, mapName).bind(complexApi)
    val id = fixtureId(protocol, Some("Smile"))
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

  private def weePickleJsonRpcCborFixture(implicit context: Context): TestFixture = {
    val codec = WeePickleCodec(WeePickleCodec.cborFactory)
    val protocol = JsonRpcProtocol[WeePickleCodec.Node, codec.type, Context](codec)
    RpcEndpoint.transport(endpointTransport).rpcProtocol(protocol).bind(simpleApi, mapName).bind(complexApi)
    val id = fixtureId(protocol, Some("CBOR"))
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

  private def weePickleJsonRpcIonFixture(implicit context: Context): TestFixture = {
    val codec = WeePickleCodec(WeePickleCodec.ionFactory)
    val protocol = JsonRpcProtocol[WeePickleCodec.Node, codec.type, Context](codec)
    RpcEndpoint.transport(endpointTransport).rpcProtocol(protocol).bind(simpleApi, mapName).bind(complexApi)
    val id = fixtureId(protocol, Some("Ion"))
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

  private def weePickleJsonRpcMessagePackFixture(implicit context: Context): TestFixture = {
    val codec = WeePickleMessagePackCodec()
    val protocol = JsonRpcProtocol[WeePickleMessagePackCodec.Node, codec.type, Context](codec)
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

  private def uPickleJsonRpcJsonFixture(implicit context: Context): TestFixture = {
    final class CustomConfig extends JsonConfig {
      implicit lazy val enumRw: ReadWriter[Enum.Enum] = readwriter[Int]
        .bimap[Enum.Enum](value => Enum.toOrdinal(value), number => Enum.fromOrdinal(number))
      implicit lazy val structureRw: ReadWriter[Structure] = macroRW
      implicit lazy val recordRw: ReadWriter[Record] = macroRW
    }
    val customConfig = new CustomConfig
    val codec = UPickleJsonCodec(customConfig)
    val protocol = JsonRpcProtocol[UPickleJsonCodec.Node, codec.type, Context](codec)
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

  private def uPickleJsonRpcMessagePackFixture(implicit context: Context): TestFixture = {
    final class CustomConfig extends MessagePackConfig {
      implicit lazy val enumRw: ReadWriter[Enum.Enum] = readwriter[Int]
        .bimap[Enum.Enum](value => Enum.toOrdinal(value), number => Enum.fromOrdinal(number))
      implicit lazy val structureRw: ReadWriter[Structure] = macroRW
      implicit lazy val recordRw: ReadWriter[Record] = macroRW
    }
    val customConfig = new CustomConfig
    val codec = UPickleMessagePackCodec(customConfig)
    val protocol = JsonRpcProtocol[UPickleMessagePackCodec.Node, codec.type, Context](codec)
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
      Functions(f => client.call[OpenApi](f)(), (f, a0) => client.call[String](f)(a0), (f, a0) => client.tell(f)(a0)),
    )
  }

  private def enumModule =
    new SimpleModule().addSerializer(
      classOf[Enum.Enum],
      new StdSerializer[Enum.Enum](classOf[Enum.Enum]) {

        override def serialize(value: Enum.Enum, generator: JsonGenerator, provider: SerializerProvider): Unit =
          generator.writeNumber(Enum.toOrdinal(value))
      },
    ).addDeserializer(
      classOf[Enum.Enum],
      new StdDeserializer[Enum.Enum](classOf[Enum.Enum]) {

        override def deserialize(parser: JsonParser, context: DeserializationContext): Enum.Enum =
          Enum.fromOrdinal(parser.getIntValue)
      },
    )
}
