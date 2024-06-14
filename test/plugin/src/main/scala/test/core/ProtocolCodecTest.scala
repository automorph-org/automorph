package test.core

import automorph.codec.UPickleJsonCodec.JsonConfig
import automorph.codec.UPickleMessagePackCodec.MessagePackConfig
import automorph.codec.{
  CirceJsonCodec, JacksonCodec, UPickleJsonCodec, UPickleMessagePackCodec, WeePickleCodec, WeePickleMessagePackCodec,
}
import automorph.protocol.JsonRpcProtocol
import automorph.schema.OpenApi
import automorph.spi.{ClientTransport, ServerTransport, RpcProtocol}
import automorph.{RpcClient, RpcServer}
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

  type OptionalServer = Option[RpcServer[?, ?, Effect, Context, ?]]

  implicit private lazy val recordFromTo: FromTo[Record] = {
    implicit val enumFromTo: FromTo[Enum.Enum] = FromTo.join(ToInt, FromInt).bimap(Enum.toOrdinal, Enum.fromOrdinal)
    implicit val structureFromTo: FromTo[Structure] = macroFromTo
    macroFromTo
  }

  override def fixtures: Seq[TestFixture] = {
    implicit val context: Context = arbitraryContext.arbitrary.sample.get
    Seq(circeJsonRpcFixture) ++ Option.when((basic && !TestLevel.simple) || TestLevel.all)(Seq(
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
      Functions(f => client.call(f)(), (f, a0) => client.call(f)(a0), (f, a0) => client.tell(f)(a0)),
    )
  }

  private def jacksonJsonRpcJsonFixture(implicit context: Context): TestFixture = {
    val codec = JacksonCodec(JacksonCodec.jsonMapper.registerModule(enumModule))
    val protocol = JsonRpcProtocol[JacksonCodec.Value, codec.type, Context](codec)
    val id = fixtureId(protocol, Some("JSON"))
    val server = RpcServer.transport(serverTransport(id)).rpcProtocol(protocol).discovery(true)
      .service(simpleApi, mapName).service(complexApi)
    val client = RpcClient.transport(typedClientTransport(id, Some(server))).rpcProtocol(protocol)
    Fixture(
      id,
      client,
      server,
      Apis(client.proxy[SimpleApiType], client.proxy[ComplexApiType], client.proxy[InvalidApiType]),
      Functions(f => client.call(f)(), (f, a0) => client.call(f)(a0), (f, a0) => client.tell(f)(a0)),
    )
  }

  private def jacksonJsonRpcSmileFixture(implicit context: Context): TestFixture = {
    val codec = JacksonCodec(JacksonCodec.smileMapper.registerModule(enumModule))
    val protocol = JsonRpcProtocol[JacksonCodec.Value, codec.type, Context](codec)
    val id = fixtureId(protocol, Some("Smile"))
    val server = RpcServer.transport(serverTransport(id)).rpcProtocol(protocol).discovery(true)
      .service(simpleApi, mapName).service(complexApi)
    val client = RpcClient.transport(typedClientTransport(id, Some(server))).rpcProtocol(protocol)
    Fixture(
      id,
      client,
      server,
      Apis(client.proxy[SimpleApiType], client.proxy[ComplexApiType], client.proxy[InvalidApiType]),
      Functions(f => client.call(f)(), (f, a0) => client.call(f)(a0), (f, a0) => client.tell(f)(a0)),
    )
  }

  private def jacksonJsonRpcCborFixture(implicit context: Context): TestFixture = {
    val codec = JacksonCodec(JacksonCodec.cborMapper.registerModule(enumModule))
    val protocol = JsonRpcProtocol[JacksonCodec.Value, codec.type, Context](codec)
    val id = fixtureId(protocol, Some("CBOR"))
    val server = RpcServer.transport(serverTransport(id)).rpcProtocol(protocol).discovery(true)
      .service(simpleApi, mapName).service(complexApi)
    val client = RpcClient.transport(typedClientTransport(id, Some(server))).rpcProtocol(protocol)
    Fixture(
      id,
      client,
      server,
      Apis(client.proxy[SimpleApiType], client.proxy[ComplexApiType], client.proxy[InvalidApiType]),
      Functions(f => client.call(f)(), (f, a0) => client.call(f)(a0), (f, a0) => client.tell(f)(a0)),
    )
  }

  private def weePickleJsonRpcJsonFixture(implicit context: Context): TestFixture = {
    val codec = WeePickleCodec()
    val protocol = JsonRpcProtocol[WeePickleCodec.Value, codec.type, Context](codec)
    val id = fixtureId(protocol, Some("JSON"))
    val server = RpcServer.transport(serverTransport(id)).rpcProtocol(protocol).discovery(true)
      .service(simpleApi, mapName).service(complexApi)
    val client = RpcClient.transport(typedClientTransport(id, Some(server))).rpcProtocol(protocol)
    Fixture(
      id,
      client,
      server,
      Apis(client.proxy[SimpleApiType], client.proxy[ComplexApiType], client.proxy[InvalidApiType]),
      Functions(f => client.call(f)(), (f, a0) => client.call(f)(a0), (f, a0) => client.tell(f)(a0)),
    )
  }

  private def weePickleJsonRpcSmileFixture(implicit context: Context): TestFixture = {
    val codec = WeePickleCodec(WeePickleCodec.smileFactory)
    val protocol = JsonRpcProtocol[WeePickleCodec.Value, codec.type, Context](codec)
    val id = fixtureId(protocol, Some("Smile"))
    val server = RpcServer.transport(serverTransport(id)).rpcProtocol(protocol).discovery(true)
      .service(simpleApi, mapName).service(complexApi)
    val client = RpcClient.transport(typedClientTransport(id, Some(server))).rpcProtocol(protocol)
    Fixture(
      id,
      client,
      server,
      Apis(client.proxy[SimpleApiType], client.proxy[ComplexApiType], client.proxy[InvalidApiType]),
      Functions(f => client.call(f)(), (f, a0) => client.call(f)(a0), (f, a0) => client.tell(f)(a0)),
    )
  }

  private def weePickleJsonRpcCborFixture(implicit context: Context): TestFixture = {
    val codec = WeePickleCodec(WeePickleCodec.cborFactory)
    val protocol = JsonRpcProtocol[WeePickleCodec.Value, codec.type, Context](codec)
    val id = fixtureId(protocol, Some("CBOR"))
    val server = RpcServer.transport(serverTransport(id)).rpcProtocol(protocol).discovery(true)
      .service(simpleApi, mapName).service(complexApi)
    val client = RpcClient.transport(typedClientTransport(id, Some(server))).rpcProtocol(protocol)
    Fixture(
      id,
      client,
      server,
      Apis(client.proxy[SimpleApiType], client.proxy[ComplexApiType], client.proxy[InvalidApiType]),
      Functions(f => client.call(f)(), (f, a0) => client.call(f)(a0), (f, a0) => client.tell(f)(a0)),
    )
  }

  private def weePickleJsonRpcIonFixture(implicit context: Context): TestFixture = {
    val codec = WeePickleCodec(WeePickleCodec.ionFactory)
    val protocol = JsonRpcProtocol[WeePickleCodec.Value, codec.type, Context](codec)
    val id = fixtureId(protocol, Some("Ion"))
    val server = RpcServer.transport(serverTransport(id)).rpcProtocol(protocol).discovery(true)
      .service(simpleApi, mapName).service(complexApi)
    val client = RpcClient.transport(typedClientTransport(id, Some(server))).rpcProtocol(protocol)
    Fixture(
      id,
      client,
      server,
      Apis(client.proxy[SimpleApiType], client.proxy[ComplexApiType], client.proxy[InvalidApiType]),
      Functions(f => client.call(f)(), (f, a0) => client.call(f)(a0), (f, a0) => client.tell(f)(a0)),
    )
  }

  private def weePickleJsonRpcMessagePackFixture(implicit context: Context): TestFixture = {
    val codec = WeePickleMessagePackCodec()
    val protocol = JsonRpcProtocol[WeePickleMessagePackCodec.Value, codec.type, Context](codec)
    val id = fixtureId(protocol)
    val server = RpcServer.transport(serverTransport(id)).rpcProtocol(protocol).discovery(true)
      .service(simpleApi, mapName).service(complexApi)
    val client = RpcClient.transport(typedClientTransport(id, Some(server))).rpcProtocol(protocol)
    Fixture(
      id,
      client,
      server,
      Apis(client.proxy[SimpleApiType], client.proxy[ComplexApiType], client.proxy[InvalidApiType]),
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
