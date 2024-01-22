package test.core

import argonaut.Argonaut.jNumber
import argonaut.{Argonaut, CodecJson}
import automorph.codec.json.{ArgonautJsonCodec, CirceJsonCodec, JacksonJsonCodec, UpickleJsonCodec, UpickleJsonCustom}
import automorph.codec.messagepack.{UpickleMessagePackCodec, UpickleMessagePackCustom}
import automorph.protocol.JsonRpcProtocol
import automorph.spi.{ClientTransport, EndpointTransport, RpcProtocol, ServerTransport}
import automorph.transport.generic.endpoint.GenericEndpoint
import automorph.{RpcClient, RpcEndpoint, RpcServer}
import com.fasterxml.jackson.core.{JsonGenerator, JsonParser}
import com.fasterxml.jackson.databind.deser.std.StdDeserializer
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.databind.ser.std.StdSerializer
import com.fasterxml.jackson.databind.{DeserializationContext, SerializerProvider}
import io.circe.generic.auto.*
import io.circe.{Decoder, Encoder}
import scala.util.Try
import test.api.{Enum, Record, Structure}
import test.base.BaseTest

trait ProtocolCodecTest extends CoreTest {
  private lazy val testFixtures: Seq[TestFixture] = {
    implicit val context: Context = arbitraryContext.arbitrary.sample.get
    Seq(context)
    createFixtures
  }

  def createFixtures(implicit context: Context): Seq[TestFixture] = {
    if (BaseTest.testAll || basic) {
      Seq(
        circeJsonRpcJsonFixture(),
        jacksonJsonRpcJsonFixture(),
        uPickleJsonRpcJsonFixture(),
        uPickleJsonRpcMessagePackFixture(),
        argonautJsonRpcJsonFixture(),
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

  override def beforeAll(): Unit = {
    super.beforeAll()
    fixtures.foreach { fixture =>
      run(fixture.genericServer.init())
    }
    fixtures.foreach { fixture =>
      run(
        system.flatMap(
          system.flatMap(
            fixture.genericClient.init()
          )(_.close())
        )(_.init())
      )
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

  override def fixtures: Seq[TestFixture] =
    Try {
      testFixtures
    }.recover {
      case error =>
        logger.error(s"Failed to initialize test fixtures")
        throw error
    }.get

  private def circeJsonRpcJsonFixture()(implicit context: Context): TestFixture = {
    implicit val enumEncoder: Encoder[Enum.Enum] = Encoder.encodeInt.contramap[Enum.Enum](Enum.toOrdinal)
    implicit val enumDecoder: Decoder[Enum.Enum] = Decoder.decodeInt.map(Enum.fromOrdinal)
    Seq(enumEncoder, enumDecoder)
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
    )
  }

  private def uPickleJsonRpcJsonFixture()(implicit context: Context): TestFixture = {
    class Custom extends UpickleJsonCustom {
      implicit lazy val enumRw: ReadWriter[Enum.Enum] = readwriter[Int]
        .bimap[Enum.Enum](value => Enum.toOrdinal(value), number => Enum.fromOrdinal(number))
      implicit lazy val structureRw: ReadWriter[Structure] = macroRW
      implicit lazy val recordRw: ReadWriter[Record] = macroRW
    }
    val custom = new Custom
    Seq(custom.enumRw, custom.structureRw, custom.recordRw)
    val codec = UpickleJsonCodec(custom)
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
    class Custom extends UpickleMessagePackCustom {
      implicit lazy val enumRw: ReadWriter[Enum.Enum] = readwriter[Int]
        .bimap[Enum.Enum](value => Enum.toOrdinal(value), number => Enum.fromOrdinal(number))
      implicit lazy val structureRw: ReadWriter[Structure] = macroRW
      implicit lazy val recordRw: ReadWriter[Record] = macroRW
    }
    val custom = new Custom
    Seq(custom.enumRw, custom.structureRw, custom.recordRw)
    val codec = UpickleMessagePackCodec(custom)
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
    implicit val recordCodecJson: CodecJson[Record] = Argonaut.codec13(
      Record.apply,
      (v: Record) =>
        (
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
        ),
    )(
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
    Seq(recordCodecJson)
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
}
