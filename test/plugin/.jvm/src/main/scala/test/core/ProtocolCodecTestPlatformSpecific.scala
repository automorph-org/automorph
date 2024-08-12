package test.core

import automorph.codec.{JacksonCodec, WeePickleCodec, WeePickleMessagePackCodec}
import automorph.protocol.JsonRpcProtocol
import automorph.{RpcClient, RpcServer}
import com.fasterxml.jackson.core.{JsonGenerator, JsonParser}
import com.fasterxml.jackson.databind.deser.std.StdDeserializer
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.databind.ser.std.StdSerializer
import com.fasterxml.jackson.databind.{DeserializationContext, SerializerProvider}
import com.rallyhealth.weepickle.v1.WeePickle.{FromInt, FromTo, ToInt, macroFromTo}
import test.api.{Enum, Record, Structure}
import test.core.Fixtures.{Apis, Fixture, Functions}

trait ProtocolCodecTestPlatformSpecific {
  self: ProtocolCodecTest =>

  implicit private lazy val recordFromTo: FromTo[Record] = {
    implicit val enumFromTo: FromTo[Enum.Enum] = FromTo.join(ToInt, FromInt).bimap(Enum.toOrdinal, Enum.fromOrdinal)
    implicit val structureFromTo: FromTo[Structure] = macroFromTo
    macroFromTo
  }

  protected def platformSpecificFixtures(implicit context: Context): Seq[TestFixture] =
    Seq(
      jacksonJsonRpcJsonFixture,
      jacksonJsonRpcSmileFixture,
      jacksonJsonRpcCborFixture,
      weePickleJsonRpcJsonFixture,
      weePickleJsonRpcSmileFixture,
      weePickleJsonRpcCborFixture,
      weePickleJsonRpcIonFixture,
      weePickleJsonRpcMessagePackFixture,
    )

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
      Functions(
        f => client.call(f)(),
        f => client.call(f)(),
        (f, a0) => client.call(f)(a0),
        (f, a0) => client.tell(f)(a0),
      ),
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
      Functions(
        f => client.call(f)(),
        f => client.call(f)(),
        (f, a0) => client.call(f)(a0),
        (f, a0) => client.tell(f)(a0),
      ),
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
      Functions(
        f => client.call(f)(),
        f => client.call(f)(),
        (f, a0) => client.call(f)(a0),
        (f, a0) => client.tell(f)(a0),
      ),
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
      Functions(
        f => client.call(f)(),
        f => client.call(f)(),
        (f, a0) => client.call(f)(a0),
        (f, a0) => client.tell(f)(a0),
      ),
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
      Functions(
        f => client.call(f)(),
        f => client.call(f)(),
        (f, a0) => client.call(f)(a0),
        (f, a0) => client.tell(f)(a0),
      ),
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
      Functions(
        f => client.call(f)(),
        f => client.call(f)(),
        (f, a0) => client.call(f)(a0),
        (f, a0) => client.tell(f)(a0),
      ),
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
      Functions(
        f => client.call(f)(),
        f => client.call(f)(),
        (f, a0) => client.call(f)(a0),
        (f, a0) => client.tell(f)(a0),
      ),
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
      Functions(
        f => client.call(f)(),
        f => client.call(f)(),
        (f, a0) => client.call(f)(a0),
        (f, a0) => client.tell(f)(a0),
      ),
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
