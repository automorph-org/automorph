---
sidebar_position: 6
---

# Plugins

*Automorph* supports integration with other libraries by providing plugins published in separate artifacts. Plugins
are separated into categories based on which part of RPC functionality they provide. Specific plugin instances can be
supplied to the factory methods of [RPC client](https://automorph.org/api/automorph/RpcClient.html),
[RPC server](https://automorph.org/api/automorph/RpcServer.html) at will.


## Default plugins

*Automorph* defines a set of default plugins aiming at a good balance of features, performance and simplicity:

- RPC protocol: [JSON-RPC](https://www.jsonrpc.org/specification)
- Message format: [JSON](https://www.json.org/)
- Transport protocol: [HTTP](https://en.wikipedia.org/wiki/HTTP)
- Synchronous effect: [Identity](https://scala-lang.org/api/3.x/scala/Predef$.html#identity-957)
- Asynchronous effect: [Future](https://scala-lang.org/api/3.x/scala/concurrent/Future.html)
- Message codec: [Circe](https://circe.github.io/circe)
- HTTP & WebSocket client: [JRE HTTP client](
https://docs.oracle.com/en/java/javase/11/docs/api/java.net.http/java/net/http/HttpClient.html)
- HTTP & WebSocket server: [Undertow](https://undertow.io/)

Default plugin set can be obtained by using the
[automorph-default](https://central.sonatype.com/artifact/org.automorph/automorph-default_3) artifact
which itself depends on selected subset of artifacts implementing the default plugins.

This artifact also contains the [Default](https://automorph.org/api/automorph/Default$.html) object
which provides a convenient way to create default plugin instances or combine default plugins with other plugins.


## [RPC protocol](https://automorph.org/api/automorph/spi/RpcProtocol.html)

| Class | Artifact | Protocol | Service discovery | Scala 2.13 | Scala 3 |
| --- | --- | --- | --- |
| [JsonRpcProtocol](https://automorph.org/api/automorph/protocol/JsonRpcProtocol.html) (*Default*) | [automorph-core](https://central.sonatype.com/artifact/org.automorph/automorph-core_3) | [JSON-RPC](https://www.jsonrpc.org/specification) | [OpenRPC](https://spec.open-rpc.org), [OpenAPI](https://www.openapis.org) | :heavy_check_mark: | :heavy_check_mark: |
| [WebRpcProtocol](https://automorph.org/api/automorph/protocol/WebRpcProtocol.html) | [automorph-core](https://central.sonatype.com/artifact/org.automorph/automorph-core_3) | [Web-RPC](Web-RPC) | [OpenAPI](https://www.openapis.org) | :heavy_check_mark: | :heavy_check_mark: |


## [Effect system](https://automorph.org/api/automorph/spi/EffectSystem.html)

| Class | Artifact | Library | Effect type | Scala 2.13 | Scala 3 |
| --- | --- | --- | --- | :---: | :---: |
| [FutureSystem](https://automorph.org/api/automorph/system/FutureSystem.html) (*Default*) | [automorph-core](https://central.sonatype.com/artifact/org.automorph/automorph-core_3) | [Standard](https://docs.scala-lang.org/overviews/core/futures.html) | [Future](https://scala-lang.org/api/3.x/scala/concurrent/Future.html) | :heavy_check_mark: | :heavy_check_mark: |
| [ZioSystem](https://automorph.org/api/automorph/system/ZioSystem.html) | [automorph-zio](https://central.sonatype.com/artifact/org.automorph/automorph-zio_3) | [ZIO](https://zio.dev) | [ZIO](https://javadoc.io/doc/dev.zio/zio_3/latest/zio.htmll#IO-0) | :heavy_check_mark: | :heavy_check_mark: |
| [MonixSystem](https://automorph.org/api/automorph/system/MonixSystem.html) | [automorph-monix](https://central.sonatype.com/artifact/org.automorph/automorph-monix_3) | [Monix](https://monix.io) | [Task](https://monix.io/api/current/monix/eval/Task.html) | :heavy_check_mark: | :heavy_check_mark: |
| [CatsEffectSystem](https://automorph.org/api/automorph/system/CatsEffectSystem.html) | [automorph-cats-effect](https://central.sonatype.com/artifact/org.automorph/automorph-cats-effect_3) | [Cats Effect](https://typelevel.org/cats-effect) | [IO](https://typelevel.org/cats-effect/api/3.x/cats/effect/IO.html) | :heavy_check_mark: | :heavy_check_mark: |
| [ScalazEffectSystem](https://automorph.org/api/automorph/system/ScalazEffectSystem.html) | [automorph-scalaz-effect](https://central.sonatype.com/artifact/org.automorph/automorph-scalaz-effect_3) | [Scalaz Effect](https://github.com/scalaz) | [IO](https://www.javadoc.io/doc/org.scalaz/scalaz_3/latest/scalaz/effect/IO.html) | :heavy_check_mark: | :heavy_check_mark: |
| [TrySystem](https://automorph.org/api/automorph/system/TrySystem.html) | [automorph-core](https://central.sonatype.com/artifact/org.automorph/automorph-core_3) | [Standard](https://docs.scala-lang.org/scala3/book/fp-functional-error-handling.html#option-isnt-the-only-solution) | [Try](https://www.scala-lang.org/files/archive/api/3.x/scala/util/Try.html) | :heavy_check_mark: | :heavy_check_mark: |
| [IdentitySystem](https://automorph.org/api/automorph/system/IdentitySystem.html) (*Default*) | [automorph-core](https://central.sonatype.com/artifact/org.automorph/automorph-core_3) | [Standard](https://docs.scala-lang.org/scala3/book/taste-functions.html) | [Identity](https://scala-lang.org/api/3.x/scala/Predef$.html#identity-957) | :heavy_check_mark: | :heavy_check_mark: |


## [Message codec](https://automorph.org/api/automorph/spi/MessageCodec.html)

| Class | Artifact | Library | Value Type | Codec | Scala 2.13 | Scala 3 |
| --- | --- | --- | --- | --- | :---: | :---: |
| [CirceJsonCodec](https://automorph.org/api/automorph/codec/json/CirceJsonCodec.html) (*Default*) | [automorph-circe](https://central.sonatype.com/artifact/org.automorph/automorph-circe_3) | [Circe](https://circe.github.io/circe) | [Json](https://circe.github.io/circe/api/io/circe/Json.html) | [JSON](https://www.json.org/) | :heavy_check_mark: | :heavy_check_mark: |
| [JacksonJsonCodec](https://automorph.org/api/automorph/codec/JacksonJsonCodec.html) | [automorph-jackson](https://central.sonatype.com/artifact/org.automorph/automorph-jackson_3) | [Jackson](https://github.com/FasterXML/jackson-module-scala) | [JsonNode](https://fasterxml.github.io/jackson-databind/javadoc/2.14/index.html?com/fasterxml/jackson/databind/JsonNode.html) | [JSON](https://www.json.org), [Smile](https://github.com/FasterXML/smile-format-specification), [CBOR](https://cbor.io) | :heavy_check_mark: | :heavy_check_mark: |
| [WeePickleCodec](https://automorph.org/api/automorph/codec/WeePickleCodec.html) | [automorph-weepickle](https://central.sonatype.com/artifact/org.automorph/automorph-weepickle_3) | [weePickle](https://github.com/rallyhealth/weePickle) | [Value](https://javadoc.io/doc/com.rallyhealth/weejson-v1_3/latest/com/rallyhealth/weejson/v1/Value.html) | [JSON](https://www.json.org), [Smile](https://github.com/FasterXML/smile-format-specification), [CBOR](https://cbor.io), [Ion](https://amazon-ion.github.io/ion-docs) | :heavy_check_mark: | :heavy_check_mark: |
| [WeePickleMessagePackCodec](https://automorph.org/api/automorph/codec/messagepack/WeePickleMessagePackCodec.html) | [automorph-weepickle](https://central.sonatype.com/artifact/org.automorph/automorph-weepickle_3) | [uPickle](https://github.com/rallyhealth/weePickle) | [Msg](https://javadoc.io/doc/com.rallyhealth/weepack-v1_3/latest/com/rallyhealth/weepack/v1/Msg.html) | [MessagePack](https://msgpack.org) | :heavy_check_mark: | :heavy_check_mark: |
| [UPickleJsonCodec](https://automorph.org/api/automorph/codec/json/UPickleJsonCodec.html) | [automorph-upickle](https://central.sonatype.com/artifact/org.automorph/automorph-upickle_3) | [uPickle](https://github.com/com-lihaoyi/upickle) | [Value](http://com-lihaoyi.github.io/upickle/#uJson) | [JSON](https://www.json.org/) | :heavy_check_mark: | :heavy_check_mark: |
| [UPickleMessagePackCodec](https://automorph.org/api/automorph/codec/messagepack/UPickleMessagePackCodec.html) | [automorph-upickle](https://central.sonatype.com/artifact/org.automorph/automorph-upickle_3) | [uPickle](https://github.com/com-lihaoyi/upickle) | [Msg](https://com-lihaoyi.github.io/upickle/#uPack) | [MessagePack](https://msgpack.org) | :heavy_check_mark: | :heavy_check_mark: |
| [PlayJsonCodec](https://automorph.org/api/automorph/codec/json/PlayJsonCodec.html) | [automorph-play-json](https://central.sonatype.com/artifact/org.automorph/automorph-play-json_3) | [Json4s](https://www.playframework.com/documentation/latest/ScalaJson) | [JsValue](https://www.playframework.com/documentation/latest/api/scala/play/api/libs/json/JsValue.html) | [JSON](https://www.json.org) | :heavy_check_mark: | |
| [Json4sNativeJsonCodec](https://automorph.org/api/automorph/codec/json/Json4sNativeJsonCodec.html) | [automorph-json4s](https://central.sonatype.com/artifact/org.automorph/automorph-json4s_3) | [Json4s](https://json4s.org) | [JValue](https://javadoc.io/doc/org.json4s/json4s-ast_2.13/latest/org/json4s/JValue.html) | [JSON](https://www.json.org) | :heavy_check_mark: | |


## Transport layer

### [Client transport](https://automorph.org/api/automorph/spi/ClientTransport.html)

| Class | Artifact | Library | Protocol | Scala 2.13 | Scala 3 |
| --- | --- | --- | --- | :---: | :---: |
| [HttpClient](https://automorph.org/api/automorph/transport/http/client/HttpClient.html) (*Default*) | [automorph-core](https://central.sonatype.com/artifact/org.automorph/automorph-core_3) | [Standard](https://openjdk.org/groups/net/httpclient/intro.html) | [HTTP](https://en.wikipedia.org/wiki/HTTP), [WebSocket](https://en.wikipedia.org/wiki/WebSocket) | :heavy_check_mark: | :heavy_check_mark: |
| [JettyClient](https://automorph.org/api/automorph/transport/http/client/JettyClient.html) | [automorph-jetty](https://central.sonatype.com/artifact/org.automorph/automorph-jetty_3) | [Jetty](https://www.eclipse.org/jetty/) | [HTTP](https://en.wikipedia.org/wiki/HTTP), [WebSocket](https://en.wikipedia.org/wiki/WebSocket) | :heavy_check_mark: | :heavy_check_mark: |
| [RabbitMqClient](https://automorph.org/api/automorph/transport/amqp/client/RabbitMqClient.html) | [automorph-rabbitmq](https://central.sonatype.com/artifact/org.automorph/automorph-rabbitmq_3) | [RabbitMQ](https://www.rabbitmq.com/java-client.html) | [AMQP](https://en.wikipedia.org/wiki/Advanced_Message_Queuing_Protocol) | :heavy_check_mark: | :heavy_check_mark: |
| [SttpClient](https://automorph.org/api/automorph/transport/http/client/SttpClient.html)| [automorph-sttp](https://central.sonatype.com/artifact/org.automorph/automorph-sttp_3) | [STTP](https://sttp.softwaremill.com/en/latest/) | | :heavy_check_mark: | :heavy_check_mark: |
| -> | [automorph-sttp](https://central.sonatype.com/artifact/org.automorph/automorph-sttp_3) | [Armeria](https://sttp.softwaremill.com/en/latest/backends/summary.html)| [HTTP](https://en.wikipedia.org/wiki/HTTP) | :heavy_check_mark: | :heavy_check_mark: |
| -> | [automorph-sttp](https://central.sonatype.com/artifact/org.automorph/automorph-sttp_3) | [AsyncHttpClient](https://sttp.softwaremill.com/en/latest/backends/summary.html)| [HTTP](https://en.wikipedia.org/wiki/HTTP), [WebSocket](https://en.wikipedia.org/wiki/WebSocket) | :heavy_check_mark: | :heavy_check_mark: |
| -> | [automorph-sttp](https://central.sonatype.com/artifact/org.automorph/automorph-sttp_3) | [HttpClient](https://sttp.softwaremill.com/en/latest/backends/summary.html)| [HTTP](https://en.wikipedia.org/wiki/HTTP), [WebSocket](https://en.wikipedia.org/wiki/WebSocket) | :heavy_check_mark: | :heavy_check_mark: |
| -> | [automorph-sttp](https://central.sonatype.com/artifact/org.automorph/automorph-sttp_3) | [OkHttp](https://sttp.softwaremill.com/en/latest/backends/summary.html)| [HTTP](https://en.wikipedia.org/wiki/HTTP), [WebSocket](https://en.wikipedia.org/wiki/WebSocket) | :heavy_check_mark: | :heavy_check_mark: |
| [UrlClient](https://automorph.org/api/automorph/transport/http/client/UrlClient.html) | [automorph-core](https://central.sonatype.com/artifact/org.automorph/automorph-core_3) | [Standard](https://docs.oracle.com/javase/8/docs/api/java/net/HttpURLConnection.html) | [HTTP](https://en.wikipedia.org/wiki/HTTP) | :heavy_check_mark: | :heavy_check_mark: |
| [LocalClient](https://automorph.org/api/automorph/transport/local/client/LocalClient.html) | [automorph-core](https://central.sonatype.com/artifact/org.automorph/automorph-core_3) | [Standard](https://docs.oracle.com/javase/8/docs/api/java/net/HttpURLConnection.html) |  | :heavy_check_mark: | :heavy_check_mark: |


### [Server transport](https://automorph.org/api/automorph/spi/ServerTransport.html)

| Class | Artifact | Library | Protocol | Scala 2.13 | Scala 3 |
| --- | --- | --- | --- | :---: | :---: |
| [UndertowServer](https://automorph.org/api/automorph/transport/http/server/UndertowServer.html) (*Default*) | [automorph-undertow](https://central.sonatype.com/artifact/org.automorph/automorph-undertow_3) | [Undertow](https://undertow.io/) | [HTTP](https://en.wikipedia.org/wiki/HTTP), [WebSocket](https://en.wikipedia.org/wiki/WebSocket) | :heavy_check_mark: | :heavy_check_mark: |
| [VertxServer](https://automorph.org/api/automorph/transport/http/server/VertxServer.html) | [automorph-vertx](https://central.sonatype.com/artifact/org.automorph/automorph-vertx_3) | [Vert.x](https://vertx.io/) | [HTTP](https://en.wikipedia.org/wiki/HTTP), [WebSocket](https://en.wikipedia.org/wiki/WebSocket) | :heavy_check_mark: | :heavy_check_mark: |
| [JettyServer](https://automorph.org/api/automorph/transport/http/server/JettyServer.html) | [automorph-jetty](https://central.sonatype.com/artifact/org.automorph/automorph-jetty_3) | [Jetty](https://www.eclipse.org/jetty/) | [HTTP](https://en.wikipedia.org/wiki/HTTP), [WebSocket](https://en.wikipedia.org/wiki/WebSocket) | :heavy_check_mark: | :heavy_check_mark: |
| [AkkaServer](https://automorph.org/api/automorph/transport/http/server/AkkaServer.html) | [automorph-akka-http](https://central.sonatype.com/artifact/org.automorph/automorph-akka-http_3) | [Akka HTTP](https://doc.akka.io/docs/akka-http/current/) | [HTTP](https://en.wikipedia.org/wiki/HTTP) | :heavy_check_mark: | :heavy_check_mark: |
| [PekkoServer](https://automorph.org/api/automorph/transport/http/server/PekkoServer.html) | [automorph-pekko-http](https://central.sonatype.com/artifact/org.automorph/automorph-pekko-http_3) | [Pekko HTTP](https://pekko.apache.org) | [HTTP](https://en.wikipedia.org/wiki/HTTP) | :heavy_check_mark: | :heavy_check_mark: |
| [RabbitMqServer](https://automorph.org/api/automorph/transport/amqp/server/RabbitMqServer.html) | [automorph-rabbitmq](https://central.sonatype.com/artifact/org.automorph/automorph-rabbitmq_3) | [RabbitMq](https://www.rabbitmq.com/java-client.html) | [AMQP](https://en.wikipedia.org/wiki/Advanced_Message_Queuing_Protocol) | :heavy_check_mark: | :heavy_check_mark: |
| [NanoServer](https://automorph.org/api/automorph/transport/http/server/NanoServer.html) | [automorph-core](https://central.sonatype.com/artifact/org.automorph/automorph-core_3) | [NanoHTTPD](https://github.com/NanoHttpd/nanohttpd) | [HTTP](https://en.wikipedia.org/wiki/HTTP), [WebSocket](https://en.wikipedia.org/wiki/WebSocket) | :heavy_check_mark: | :heavy_check_mark: |


### [Endpoint transport](https://automorph.org/api/automorph/spi/ServerTransport.html)

| Class | Artifact | Library | Protocol | Scala 2.13 | Scala 3 |
| --- | --- | --- | --- | :---: | :---: |
| [GenericEndpoint](https://automorph.org/api/automorph/transport/generic/endpoint/GenericEndpoint.html) | [automorph-core](https://central.sonatype.com/artifact/org.automorph/automorph-core_3) |  |  | :heavy_check_mark: | :heavy_check_mark: |
| [UndertowHttpEndpoint](https://automorph.org/api/automorph/transport/http/endpoint/UndertowHttpEndpoint.html) | [automorph-undertow](https://central.sonatype.com/artifact/org.automorph/automorph-undertow_3) | [Undertow](https://undertow.io/) | [HTTP](https://en.wikipedia.org/wiki/HTTP) | :heavy_check_mark: | :heavy_check_mark: |
| [UndertowWebSocketEndpoint](https://automorph.org/api/automorph/transport/websocket/endpoint/UndertowWebSocketEndpoint.html) | [automorph-undertow](https://central.sonatype.com/artifact/org.automorph/automorph-undertow_3) | [Undertow](https://undertow.io/) | [WebSocket](https://en.wikipedia.org/wiki/WebSocket) | :heavy_check_mark: | :heavy_check_mark: |
| [VertxHttpEndpoint](https://automorph.org/api/automorph/transport/http/endpoint/VertxHttpEndpoint.html) | [automorph-vertx](https://central.sonatype.com/artifact/org.automorph/automorph-vertx_3) | [Vert.x](https://vertx.io/) | [HTTP](https://en.wikipedia.org/wiki/HTTP) | :heavy_check_mark: | :heavy_check_mark: |
| [VertxWebSocketEndpoint](https://automorph.org/api/automorph/transport/websocket/endpoint/VertxWebSocketEndpoint.html) | [automorph-vertx](https://central.sonatype.com/artifact/org.automorph/automorph-vertx_3) | [Vert.x](https://vertx.io/) | [WebSocket](https://en.wikipedia.org/wiki/WebSocket) | :heavy_check_mark: | :heavy_check_mark: |
| [JettyHttpEndpoint](https://automorph.org/api/automorph/transport/http/endpoint/JettyHttpEndpoint.html) | [automorph-jetty](https://central.sonatype.com/artifact/org.automorph/automorph-jetty_3) | [Jetty](https://www.eclipse.org/jetty/) | [HTTP](https://en.wikipedia.org/wiki/HTTP) | :heavy_check_mark: | :heavy_check_mark: |
| [JettyWebSocketEndpoint](https://automorph.org/api/automorph/transport/http/endpoint/JettyWebSocketEndpoint.html) | [automorph-jetty](https://central.sonatype.com/artifact/org.automorph/automorph-jetty_3) | [Jetty](https://www.eclipse.org/jetty/) | [HTTP](https://en.wikipedia.org/wiki/WebSocket) | :heavy_check_mark: | :heavy_check_mark: |
| [AkkaHttpEndpoint](https://automorph.org/api/automorph/transport/http/endpoint/AkkaHttpEndpoint.html) | [automorph-akka-http](https://central.sonatype.com/artifact/org.automorph/automorph-akka-http_3) | [Akka HTTP](https://doc.akka.io/docs/akka-http/current/) | [HTTP](https://en.wikipedia.org/wiki/HTTP) | :heavy_check_mark: | :heavy_check_mark: |
| [ZioHttpEndpoint](https://automorph.org/api/automorph/transport/http/endpoint/ZioHttpEndpoint.html) | [automorph-zio-http](https://central.sonatype.com/artifact/org.automorph/automorph-zio-http_3) | [Zio HTTP](https://zio.dev/zio-http) | [HTTP](https://en.wikipedia.org/wiki/HTTP) | :heavy_check_mark: | :heavy_check_mark: |
| [PekkoHttpEndpoint](https://automorph.org/api/automorph/transport/http/endpoint/PekkoHttpEndpoint.html) | [automorph-pekko-http](https://central.sonatype.com/artifact/org.automorph/automorph-pekko-http_3) | [Pekko HTTP](https://pekko.apache.org) | [HTTP](https://en.wikipedia.org/wiki/HTTP) | :heavy_check_mark: | :heavy_check_mark: |
| [FinagleEndpoint](https://automorph.org/api/automorph/transport/http/endpoint/FinagleHttpEndpoint.html) | [automorph-finagle](https://central.sonatype.com/artifact/org.automorph/automorph-finagle_3) | [Finagle](https://twitter.github.io/finagle/) | [HTTP](https://en.wikipedia.org/wiki/HTTP) | :heavy_check_mark: | :heavy_check_mark: |
| [TapirHttpEndpoint](https://automorph.org/api/automorph/transport/http/endpoint/TapirHttpEndpoint.html) | [automorph-tapir](https://central.sonatype.com/artifact/org.automorph/automorph-tapir_3) | [Tapir](https://tapir.softwaremill.com/) | | :heavy_check_mark: | :heavy_check_mark: |
| -> | [automorph-tapir](https://central.sonatype.com/artifact/org.automorph/automorph-tapir_3) | [Armeria](https://tapir.softwaremill.com/en/latest/server/armeria.html)| [HTTP](https://en.wikipedia.org/wiki/HTTP) | :heavy_check_mark: | :heavy_check_mark: |
| -> | [automorph-tapir](https://central.sonatype.com/artifact/org.automorph/automorph-tapir_3) | [Vert.x](https://tapir.softwaremill.com/en/latest/server/vertx.html)| [HTTP](https://en.wikipedia.org/wiki/HTTP) | :heavy_check_mark: | :heavy_check_mark: |
| -> | [automorph-tapir](https://central.sonatype.com/artifact/org.automorph/automorph-tapir_3) | [Http4s](https://tapir.softwaremill.com/en/latest/server/http4s.html)| [HTTP](https://en.wikipedia.org/wiki/HTTP) | :heavy_check_mark: | :heavy_check_mark: |
| -> | [automorph-tapir](https://central.sonatype.com/artifact/org.automorph/automorph-tapir_3) | [Netty](https://tapir.softwaremill.com/en/latest/server/netty.html)| [HTTP](https://en.wikipedia.org/wiki/HTTP) | :heavy_check_mark: | :heavy_check_mark: |

