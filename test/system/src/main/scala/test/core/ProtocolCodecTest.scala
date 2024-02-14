package test.core

import automorph.codec.{JacksonCodec, WeepickleCodec}
import automorph.codec.json.{CirceJsonCodec, UpickleJsonCodec, UpickleJsonConfig}
import automorph.codec.messagepack.{UpickleMessagePackCodec, UpickleMessagePackConfig}
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
import test.api.{Enum, Record, Structure}
import test.base.BaseTest
import scala.annotation.nowarn

@nowarn("msg=used")
trait ProtocolCodecTest extends CoreTest {

  override def fixtures: Seq[TestFixture] = {
    implicit val context: Context = arbitraryContext.arbitrary.sample.get
    Seq(circeJsonRpcJsonFixture()) ++ Option.when(basic || BaseTest.testAll)(Seq(
      jacksonJsonRpcJsonFixture(),
      jacksonJsonRpcSmileFixture(),
      jacksonJsonRpcCborFixture(),
      weePickleJsonRpcJsonFixture(),
      weePickleJsonRpcSmileFixture(),
      weePickleJsonRpcCborFixture(),
      weePickleJsonRpcIonFixture(),
      uPickleJsonRpcJsonFixture(),
      uPickleJsonRpcMessagePackFixture(),
    )).getOrElse(Seq.empty)
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

  private def circeJsonRpcJsonFixture()(implicit context: Context): TestFixture = {
    implicit val enumEncoder: Encoder[Enum.Enum] = Encoder.encodeInt.contramap[Enum.Enum](Enum.toOrdinal)
    implicit val enumDecoder: Decoder[Enum.Enum] = Decoder.decodeInt.map(Enum.fromOrdinal)
    val codec = CirceJsonCodec()
    val protocol = JsonRpcProtocol[CirceJsonCodec.Node, codec.type, Context](codec)
    RpcEndpoint.transport(endpointTransport).rpcProtocol(protocol).bind(simpleApi, mapName).bind(complexApi)
    val id = fixtureId(protocol)
    val server = RpcServer.transport(serverTransport(id)).rpcProtocol(protocol).discovery(true)
      .bind(simpleApi, mapName).bind(complexApi)
    val client = RpcClient.transport(typedClientTransport(id)).rpcProtocol(protocol)
    TestFixture(
      id, client, server,
      TestApis(client.bind[SimpleApiType], client.bind[ComplexApiType], client.bind[InvalidApiType]),
      TestCalls(f => client.call(f)(), (f, a0) => client.call(f)(a0), (f, a0) => client.tell(f)(a0)),
    )
  }

  private def jacksonJsonRpcJsonFixture()(implicit context: Context): TestFixture = {
    val enumModule = new SimpleModule().addSerializer(
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
    val codec = JacksonCodec(JacksonCodec.jsonMapper.registerModule(enumModule))
    val protocol = JsonRpcProtocol[JacksonCodec.Node, codec.type, Context](codec)
    RpcEndpoint.transport(endpointTransport).rpcProtocol(protocol).bind(simpleApi, mapName).bind(complexApi)
    val id = fixtureId(protocol, Some("JSON"))
    val server = RpcServer.transport(serverTransport(id)).rpcProtocol(protocol).discovery(true)
      .bind(simpleApi, mapName).bind(complexApi)
    val client = RpcClient.transport(typedClientTransport(id)).rpcProtocol(protocol)
    TestFixture(
      id, client, server,
      TestApis(client.bind[SimpleApiType], client.bind[ComplexApiType], client.bind[InvalidApiType]),
      TestCalls(f => client.call(f)(), (f, a0) => client.call(f)(a0), (f, a0) => client.tell(f)(a0)),
    )
  }

  private def jacksonJsonRpcSmileFixture()(implicit context: Context): TestFixture = {
    val codec = JacksonCodec(JacksonCodec.smileMapper.registerModule(enumModule))
    val protocol = JsonRpcProtocol[JacksonCodec.Node, codec.type, Context](codec)
    RpcEndpoint.transport(endpointTransport).rpcProtocol(protocol).bind(simpleApi, mapName).bind(complexApi)
    val id = fixtureId(protocol, Some("Smile"))
    val server = RpcServer.transport(serverTransport(id)).rpcProtocol(protocol).discovery(true)
      .bind(simpleApi, mapName).bind(complexApi)
    val client = RpcClient.transport(typedClientTransport(id)).rpcProtocol(protocol)
    TestFixture(
      id, client, server,
      TestApis(client.bind[SimpleApiType], client.bind[ComplexApiType], client.bind[InvalidApiType]),
      TestCalls(f => client.call(f)(), (f, a0) => client.call(f)(a0), (f, a0) => client.tell(f)(a0)),
    )
  }

  private def jacksonJsonRpcCborFixture()(implicit context: Context): TestFixture = {
    val codec = JacksonCodec(JacksonCodec.cborMapper.registerModule(enumModule))
    val protocol = JsonRpcProtocol[JacksonCodec.Node, codec.type, Context](codec)
    RpcEndpoint.transport(endpointTransport).rpcProtocol(protocol).bind(simpleApi, mapName).bind(complexApi)
    val id = fixtureId(protocol, Some("CBOR"))
    val server = RpcServer.transport(serverTransport(id)).rpcProtocol(protocol).discovery(true)
      .bind(simpleApi, mapName).bind(complexApi)
    val client = RpcClient.transport(typedClientTransport(id)).rpcProtocol(protocol)
    TestFixture(
      id, client, server,
      TestApis(client.bind[SimpleApiType], client.bind[ComplexApiType], client.bind[InvalidApiType]),
      TestCalls(f => client.call(f)(), (f, a0) => client.call(f)(a0), (f, a0) => client.tell(f)(a0)),
    )
  }

  private def weePickleJsonRpcJsonFixture()(implicit context: Context): TestFixture = {
    val codec = WeepickleCodec()
    val protocol = JsonRpcProtocol[WeepickleCodec.Node, codec.type, Context](codec)
    RpcEndpoint.transport(endpointTransport).rpcProtocol(protocol).bind(simpleApi, mapName).bind(complexApi)
    val id = fixtureId(protocol, Some("JSON"))
    val server = RpcServer.transport(serverTransport(id)).rpcProtocol(protocol).discovery(true)
      .bind(simpleApi, mapName).bind(complexApi)
    val client = RpcClient.transport(typedClientTransport(id)).rpcProtocol(protocol)
    TestFixture(
      id, client, server,
      TestApis(client.bind[SimpleApiType], client.bind[ComplexApiType], client.bind[InvalidApiType]),
      TestCalls(f => client.call(f)(), (f, a0) => client.call(f)(a0), (f, a0) => client.tell(f)(a0)),
    )
  }

  private def weePickleJsonRpcSmileFixture()(implicit context: Context): TestFixture = {
    val codec = WeepickleCodec(WeepickleCodec.smileFactory)
    val protocol = JsonRpcProtocol[WeepickleCodec.Node, codec.type, Context](codec)
    RpcEndpoint.transport(endpointTransport).rpcProtocol(protocol).bind(simpleApi, mapName).bind(complexApi)
    val id = fixtureId(protocol, Some("Smile"))
    val server = RpcServer.transport(serverTransport(id)).rpcProtocol(protocol).discovery(true)
      .bind(simpleApi, mapName).bind(complexApi)
    val client = RpcClient.transport(typedClientTransport(id)).rpcProtocol(protocol)
    TestFixture(
      id, client, server,
      TestApis(client.bind[SimpleApiType], client.bind[ComplexApiType], client.bind[InvalidApiType]),
      TestCalls(f => client.call(f)(), (f, a0) => client.call(f)(a0), (f, a0) => client.tell(f)(a0)),
    )
  }

  private def weePickleJsonRpcCborFixture()(implicit context: Context): TestFixture = {
    val codec = WeepickleCodec(WeepickleCodec.cborFactory)
    val protocol = JsonRpcProtocol[WeepickleCodec.Node, codec.type, Context](codec)
    RpcEndpoint.transport(endpointTransport).rpcProtocol(protocol).bind(simpleApi, mapName).bind(complexApi)
    val id = fixtureId(protocol, Some("CBOR"))
    val server = RpcServer.transport(serverTransport(id)).rpcProtocol(protocol).discovery(true)
      .bind(simpleApi, mapName).bind(complexApi)
    val client = RpcClient.transport(typedClientTransport(id)).rpcProtocol(protocol)
    TestFixture(
      id, client, server,
      TestApis(client.bind[SimpleApiType], client.bind[ComplexApiType], client.bind[InvalidApiType]),
      TestCalls(f => client.call(f)(), (f, a0) => client.call(f)(a0), (f, a0) => client.tell(f)(a0)),
    )
  }

  private def weePickleJsonRpcIonFixture()(implicit context: Context): TestFixture = {
    val codec = WeepickleCodec(WeepickleCodec.ionFactory)
    val protocol = JsonRpcProtocol[WeepickleCodec.Node, codec.type, Context](codec)
    RpcEndpoint.transport(endpointTransport).rpcProtocol(protocol).bind(simpleApi, mapName).bind(complexApi)
    val id = fixtureId(protocol, Some("Ion"))
    val server = RpcServer.transport(serverTransport(id)).rpcProtocol(protocol).discovery(true)
      .bind(simpleApi, mapName).bind(complexApi)
    val client = RpcClient.transport(typedClientTransport(id)).rpcProtocol(protocol)
    TestFixture(
      id, client, server,
      TestApis(client.bind[SimpleApiType], client.bind[ComplexApiType], client.bind[InvalidApiType]),
      TestCalls(f => client.call(f)(), (f, a0) => client.call(f)(a0), (f, a0) => client.tell(f)(a0)),
    )
  }

  private def uPickleJsonRpcJsonFixture()(implicit context: Context): TestFixture = {
    class CustomConfig extends UpickleJsonConfig {
      implicit lazy val enumRw: ReadWriter[Enum.Enum] = readwriter[Int]
        .bimap[Enum.Enum](value => Enum.toOrdinal(value), number => Enum.fromOrdinal(number))
      implicit lazy val structureRw: ReadWriter[Structure] = macroRW
      implicit lazy val recordRw: ReadWriter[Record] = macroRW
    }
    val customConfig = new CustomConfig
    val codec = UpickleJsonCodec(customConfig)
    val protocol = JsonRpcProtocol[UpickleJsonCodec.Node, codec.type, Context](codec)
    RpcEndpoint.transport(endpointTransport).rpcProtocol(protocol).bind(simpleApi, mapName).bind(complexApi)
    val id = fixtureId(protocol)
    val server = RpcServer.transport(serverTransport(id)).rpcProtocol(protocol).discovery(true)
      .bind(simpleApi, mapName).bind(complexApi)
    val client = RpcClient.transport(typedClientTransport(id)).rpcProtocol(protocol)
    TestFixture(
      id, client, server,
      TestApis(client.bind[SimpleApiType], client.bind[ComplexApiType], client.bind[InvalidApiType]),
      TestCalls(f => client.call(f)(), (f, a0) => client.call(f)(a0), (f, a0) => client.tell(f)(a0)),
    )
  }

  private def uPickleJsonRpcMessagePackFixture()(implicit context: Context): TestFixture = {
    class CustomConfig extends UpickleMessagePackConfig {
      implicit lazy val enumRw: ReadWriter[Enum.Enum] = readwriter[Int]
        .bimap[Enum.Enum](value => Enum.toOrdinal(value), number => Enum.fromOrdinal(number))
      implicit lazy val structureRw: ReadWriter[Structure] = macroRW
      implicit lazy val recordRw: ReadWriter[Record] = macroRW
    }
    val customConfig = new CustomConfig
    val codec = UpickleMessagePackCodec(customConfig)
    val protocol = JsonRpcProtocol[UpickleMessagePackCodec.Node, codec.type, Context](codec)
    RpcEndpoint.transport(endpointTransport).rpcProtocol(protocol).bind(simpleApi, mapName).bind(complexApi)
    val id = fixtureId(protocol)
    val server = RpcServer.transport(serverTransport(id)).rpcProtocol(protocol).discovery(true)
      .bind(simpleApi, mapName).bind(complexApi)
    val client = RpcClient.transport(typedClientTransport(id)).rpcProtocol(protocol)
    TestFixture(
      id, client, server,
      TestApis(client.bind[SimpleApiType], client.bind[ComplexApiType], client.bind[InvalidApiType]),
      TestCalls(f => client.call[OpenApi](f)(), (f, a0) => client.call[String](f)(a0), (f, a0) => client.tell(f)(a0)),
    )
  }

  private def enumModule =
    new SimpleModule().addSerializer(classOf[Enum.Enum], new StdSerializer[Enum.Enum](classOf[Enum.Enum]) {

      override def serialize(value: Enum.Enum, generator: JsonGenerator, provider: SerializerProvider): Unit =
        generator.writeNumber(Enum.toOrdinal(value))
    }).addDeserializer(classOf[Enum.Enum], new StdDeserializer[Enum.Enum](classOf[Enum.Enum]) {

      override def deserialize(parser: JsonParser, context: DeserializationContext): Enum.Enum =
        Enum.fromOrdinal(parser.getIntValue)
    })

  implicit private lazy val recordFromTo: FromTo[Record] = {
    implicit val enumFromTo: FromTo[Enum.Enum] = FromTo.join(ToInt, FromInt).bimap(Enum.toOrdinal, Enum.fromOrdinal)
    implicit val structureFromTo: FromTo[Structure] = macroFromTo
    macroFromTo
  }
}
