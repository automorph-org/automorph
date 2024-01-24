package test.core

import argonaut.Argonaut.jNumber
import argonaut.{Argonaut, CodecJson}
import automorph.codec.{JacksonCodec, WeepickleCodec}
import automorph.codec.json.{ArgonautJsonCodec, CirceJsonCodec, UpickleJsonCodec, UpickleJsonConfig}
import automorph.codec.messagepack.{UpickleMessagePackCodec, UpickleMessagePackConfig}
import automorph.protocol.JsonRpcProtocol
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
import scala.util.{Failure, Try}
import test.api.{Enum, Record, Structure}
import test.base.BaseTest
import scala.annotation.nowarn

@nowarn("msg=used")
trait ProtocolCodecTest extends CoreTest {
  private lazy val testFixtures: Seq[TestFixture] = {
    implicit val context: Context = arbitraryContext.arbitrary.sample.get
    createFixtures
  }
  implicit private lazy val recordFromTo: FromTo[Record] = {
    implicit val enumFromTo: FromTo[Enum.Enum] = FromTo.join(ToInt, FromInt).bimap(Enum.toOrdinal, Enum.fromOrdinal)
    implicit val structureFromTo: FromTo[Structure] = macroFromTo
    macroFromTo
  }

  def createFixtures(implicit context: Context): Seq[TestFixture] = {
    if (BaseTest.testAll || basic) {
      Seq(
        //          circeJsonRpcJsonFixture(),
        //          jacksonJsonRpcJsonFixture(),
        //          jacksonJsonRpcCborFixture(),
        //          jacksonJsonRpcSmileFixture(),
        weePickleJsonRpcJsonFixture(5),
        //          weePickleJsonRpcCborFixture(),
        //          weePickleJsonRpcSmileFixture(),
        //          uPickleJsonRpcJsonFixture(),
        //          uPickleJsonRpcMessagePackFixture(),
        //          argonautJsonRpcJsonFixture(),
      )
    } else {
      Seq(circeJsonRpcJsonFixture())
    }
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

  def fixtureId(rpcProtocol: RpcProtocol[?, ?, ?]): String = {
    val codecName = rpcProtocol.messageCodec.getClass.getSimpleName.replaceAll("MessageCodec$", "")
    s"${rpcProtocol.name} / $codecName"
  }

  override def fixtures: Seq[TestFixture] =
    testFixtures

  override def beforeAll(): Unit = {
    super.beforeAll()
    fixtures.foreach { fixture =>
      Try(run(fixture.genericServer.init())).recoverWith {
        case error =>
          Failure(new IllegalStateException(s"Failed to initialize server: ${getClass.getName} / ${fixture.id}", error))
      }.get
    }
    fixtures.foreach { fixture =>
      Try(run(fixture.genericClient.init())).recoverWith {
        case error =>
          Failure(new IllegalStateException(s"Failed to initialize client: ${getClass.getName} / ${fixture.id}", error))
      }.get
    }
  }

  override def afterAll(): Unit = {
    fixtures.foreach { fixture =>
      run(fixture.genericClient.close())
    }
    fixtures.foreach { fixture =>
      run(fixture.genericServer.close())
    }
    super.afterAll()
  }

  private def circeJsonRpcJsonFixture()(implicit context: Context): TestFixture = {
    implicit val enumEncoder: Encoder[Enum.Enum] = Encoder.encodeInt.contramap[Enum.Enum](Enum.toOrdinal)
    implicit val enumDecoder: Decoder[Enum.Enum] = Decoder.decodeInt.map(Enum.fromOrdinal)
    val codec = CirceJsonCodec()
    val protocol = JsonRpcProtocol[CirceJsonCodec.Node, codec.type, Context](codec)
    RpcEndpoint.transport(endpointTransport).rpcProtocol(protocol).bind(simpleApi, mapName).bind(complexApi)
    val id = fixtureId(protocol)
    val server =
      RpcServer.transport(serverTransport(id)).rpcProtocol(protocol).bind(simpleApi, mapName).bind(complexApi)
    val client = RpcClient.transport(typedClientTransport(id)).rpcProtocol(protocol)
    TestFixture(
      id,
      client,
      server,
      client.bind[SimpleApiType],
      client.bind[ComplexApiType],
      client.bind[InvalidApiType],
      (function, a0) => client.call[String](function)(a0),
      (function, a0) => client.tell(function)(a0),
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
    val codec = JacksonJsonCodec(JacksonJsonCodec.defaultMapper.registerModule(enumModule))
    val protocol = JsonRpcProtocol[JacksonJsonCodec.Node, codec.type, Context](codec)
    RpcEndpoint.transport(endpointTransport).rpcProtocol(protocol).bind(simpleApi, mapName).bind(complexApi)
    val id = fixtureId(protocol)
    val server =
      RpcServer.transport(serverTransport(id)).rpcProtocol(protocol).bind(simpleApi, mapName).bind(complexApi)
    val client = RpcClient.transport(typedClientTransport(id)).rpcProtocol(protocol)
    TestFixture(
      id,
      client,
      server,
      client.bind[SimpleApiType],
      client.bind[ComplexApiType],
      client.bind[InvalidApiType],
      (function, a0) => client.call[String](function)(a0),
      (function, a0) => client.tell(function)(a0),
      " / JSON",
    )
  }

  private def jacksonJsonRpcCborFixture(id: Int)(implicit context: Context): TestFixture = {
    val codec = JacksonCodec(JacksonCodec.cborMapper.registerModule(enumModule))
    val protocol = JsonRpcProtocol[JacksonCodec.Node, codec.type, Context](codec)
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
      " / CBOR",
    )
  }

  private def jacksonJsonRpcSmileFixture(id: Int)(implicit context: Context): TestFixture = {
    val codec = JacksonCodec(JacksonCodec.smileMapper.registerModule(enumModule))
    val protocol = JsonRpcProtocol[JacksonCodec.Node, codec.type, Context](codec)
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
      " / Smile",
    )
  }

  private def weePickleJsonRpcJsonFixture(id: Int)(implicit context: Context): TestFixture = {
    val codec = WeepickleCodec()
    val protocol = JsonRpcProtocol[WeepickleCodec.Node, codec.type, Context](codec)
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
      " / JSON",
    )
  }

  private def weePickleJsonRpcCborFixture(id: Int)(implicit context: Context): TestFixture = {
    val codec = WeepickleCodec(WeepickleCodec.cborFactory)
    val protocol = JsonRpcProtocol[WeepickleCodec.Node, codec.type, Context](codec)
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
      " / CBOR",
    )
  }

  private def weePickleJsonRpcSmileFixture(id: Int)(implicit context: Context): TestFixture = {
    val codec = WeepickleCodec(WeepickleCodec.smileFactory)
    val protocol = JsonRpcProtocol[WeepickleCodec.Node, codec.type, Context](codec)
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
      " / Smile",
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
    val server =
      RpcServer.transport(serverTransport(id)).rpcProtocol(protocol).bind(simpleApi, mapName).bind(complexApi)
    val client = RpcClient.transport(typedClientTransport(id)).rpcProtocol(protocol)
    TestFixture(
      id,
      client,
      server,
      client.bind[SimpleApiType],
      client.bind[ComplexApiType],
      client.bind[InvalidApiType],
      (function, a0) => client.call[String](function)(a0),
      (function, a0) => client.tell(function)(a0),
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
    val server =
      RpcServer.transport(serverTransport(id)).rpcProtocol(protocol).bind(simpleApi, mapName).bind(complexApi)
    val client = RpcClient.transport(typedClientTransport(id)).rpcProtocol(protocol)
    TestFixture(
      id,
      client,
      server,
      client.bind[SimpleApiType],
      client.bind[ComplexApiType],
      client.bind[InvalidApiType],
      (function, a0) => client.call[String](function)(a0),
      (function, a0) => client.tell(function)(a0),
    )
  }

  private def argonautJsonRpcJsonFixture()(implicit context: Context): TestFixture = {
    implicit val enumCodecJson: CodecJson[Enum.Enum] =
      CodecJson((v: Enum.Enum) => jNumber(Enum.toOrdinal(v)), cursor => cursor.focus.as[Int].map(Enum.fromOrdinal))
    implicit val structureCodecJson: CodecJson[Structure] = Argonaut
      .codec1(Structure.apply, (v: Structure) => v.value)("value")
    implicit val recordCodecJson: CodecJson[Record] = Argonaut.codec13(Record.apply, (v: Record) => (
      v.string,
      v.boolean,
      v.byte,
      v.short,
      v.int,
      v.long,
      v.float,
      v.double,
      v.enumeration,
      v.list,
      v.map,
      v.structure,
      v.none,
    ))(
      "string",
      "boolean",
      "byte",
      "short",
      "int",
      "long",
      "float",
      "double",
      "enumeration",
      "list",
      "map",
      "structure",
      "none",
    )
    val codec = ArgonautJsonCodec()
    val protocol = JsonRpcProtocol[ArgonautJsonCodec.Node, codec.type, Context](codec)
    RpcEndpoint.transport(endpointTransport).rpcProtocol(protocol).bind(simpleApi, mapName).bind(complexApi)
    val id = fixtureId(protocol)
    val server =
      RpcServer.transport(serverTransport(id)).rpcProtocol(protocol).bind(simpleApi, mapName).bind(complexApi)
    val client = RpcClient.transport(typedClientTransport(id)).rpcProtocol(protocol)
    TestFixture(
      id,
      client,
      server,
      client.bind[SimpleApiType],
      client.bind[ComplexApiType],
      client.bind[InvalidApiType],
      (function, a0) => client.call[String](function)(a0),
      (function, a0) => client.tell(function)(a0),
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
}
