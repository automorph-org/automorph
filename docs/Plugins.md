---
sidebar_position: 5
---

# Plugins

*Automorph* supports integration with other libraries by providing plugins published in separate artifacts. Plugins
are separated into categories based on which part of RPC functionality they provide. Specific plugins can be
instantiated, configured and combined at will.


## Defaults

*Automorph* defines a set of default plugins aiming at a good balance of features, performance and simplicity:

* RPC protocol: [JSON-RPC](https://www.jsonrpc.org/specification)
* Message format: [JSON](https://www.json.org/)
* Transport protocol: [HTTP](https://en.wikipedia.org/wiki/HTTP )
* Synchronous effect: [Identity](https://scala-lang.org/api/3.x/scala/Predef$.html#identity-957)
* Asynchronous effect: [Future](https://scala-lang.org/api/3.x/scala/concurrent/Future.html)
* Message codec: [Circe](https://circe.github.io/circe)
* HTTP & WebSocket client: [JRE HTTP client](
https://docs.oracle.com/en/java/javase/11/docs/api/java.net.http/java/net/http/HttpClient.html)
* HTTP & WebSocket server: [Undertow](https://undertow.io/)

Default plugin set can be obtained by using the [automorph-default](
https://central.sonatype.com/artifact/org.automorph/automorph-default_3) artifact which itself depends on selected subset of
artifacts implementing the default plugins. This artifact also contains the [Default](/api/automorph/Default$.html)
object which provides a convenient way to create default plugin instances or combine default plugins with other plugins.


## [RPC protocol](/api/automorph/spi/RpcProtocol.html)

| Class | Artifact | Protocol | Service discovery |
| --- | --- | --- | --- |
| [JsonRpcProtocol](/api/automorph/protocol/JsonRpcProtocol.html) (*Default*) | [automorph-jsonrpc](https://central.sonatype.com/artifact/org.automorph/automorph-core_3) | [JSON-RPC](https://www.jsonrpc.org/specification) | [OpenRPC](https://spec.open-rpc.org), [OpenAPI](https://github.com/OAI/OpenAPI-Specification) |
| [WebRpcProtocol](/api/automorph/protocol/WebRpcProtocol.html) | [automorph-restrpc](https://central.sonatype.com/artifact/org.automorph/automorph-core_3) | [Web-RPC](Web-RPC) | [OpenAPI](https://github.com/OAI/OpenAPI-Specification) |


## [Effect system](/api/automorph/spi/EffectSystem.html)

| Class | Artifact | Library | Effect type |
| --- | --- | --- | --- |
| [IdentitySystem](/api/automorph/system/IdentitySystem.html) (Default) | [automorph-core](https://central.sonatype.com/artifact/org.automorph/automorph-core_3) | [Standard](https://docs.scala-lang.org/scala3/book/taste-functions.html) | [Identity](https://scala-lang.org/api/3.x/scala/Predef$.html#identity-957) |
| [TrySystem](/api/automorph/system/TrySystem.html) | [automorph-core](https://central.sonatype.com/artifact/org.automorph/automorph-core_3) | [Standard](https://docs.scala-lang.org/scala3/book/fp-functional-error-handling.html) | [Try](https://www.scala-lang.org/files/archive/api/3.x/scala/util/Try.html) |
| [FutureSystem](/api/automorph/system/FutureSystem.html) (*Default*) | [automorph-core](https://central.sonatype.com/artifact/org.automorph/automorph-core_3) | [Standard](https://docs.scala-lang.org/overviews/core/futures.html) | [Future](https://scala-lang.org/api/3.x/scala/concurrent/Future.html) |
| [ZioSystem](/api/automorph/system/ZioSystem.html) | [automorph-zio](https://central.sonatype.com/artifact/org.automorph/automorph-zio_3) | [ZIO](https://zio.dev/) | [RIO](https://javadoc.io/doc/dev.zio/zio_3/latest/zio.html#RIO-0) |
| [MonixSystem](/api/automorph/system/MonixSystem.html) | [automorph-monix](https://central.sonatype.com/artifact/org.automorph/automorph-monix_3) | [Monix](https://monix.io/) | [Task](https://monix.io/api/current/monix/eval/Task.html) |
| [CatsEffectSystem](/api/automorph/system/CatsEffectSystem.html) | [automorph-cats-effect](https://central.sonatype.com/artifact/org.automorph/automorph-cats-effect_3) | [Cats Effect](https://typelevel.org/cats-effect/) | [IO](https://typelevel.org/cats-effect/api/3.x/cats/effect/IO.html) |
| [ScalazEffectSystem](/api/automorph/system/ScalazEffectSystem.html) | [automorph-scalaz-effect](https://central.sonatype.com/artifact/org.automorph/automorph-scalaz-effect_3) | [Scalaz Effect](https://github.com/scalaz) | [IO](https://www.javadoc.io/doc/org.scalaz/scalaz_3/latest/scalaz/effect/IO.html) |


## [Message codec](/api/automorph/spi/MessageCodec.html)

| Class | Artifact | Library | Node Type | Codec |
| --- | --- | --- | --- | --- |
| [CirceJsonCodec](/api/automorph/codec/json/CirceJsonCodec.html) (*Default*) | [automorph-circe](https://central.sonatype.com/artifact/org.automorph/automorph-circe_3) | [Circe](https://circe.github.io/circe) |[Json](https://circe.github.io/circe/api/io/circe/Json.html) | [JSON](https://www.json.org/) |
| [JacksonJsonCodec](/api/automorph/codec/json/JacksonJsonCodec.html) | [automorph-jackson](https://central.sonatype.com/artifact/org.automorph/automorph-jackson_3) | [Jackson](https://github.com/FasterXML/jackson-module-scala/) |[JsonNode](https://fasterxml.github.io/jackson-databind/javadoc/2.14/index.html?com/fasterxml/jackson/databind/JsonNode.html) | [JSON](https://www.json.org/) |
| [UpickleJsonCodec](/api/automorph/codec/json/UpickleJsonCodec.html) | [automorph-upickle](https://central.sonatype.com/artifact/org.automorph/automorph-upickle_3) | [uPickle](https://github.com/com-lihaoyi/upickle) |[Value](http://com-lihaoyi.github.io/upickle/#uJson) | [JSON](https://www.json.org/) |
| [UpickleMessagePackCodec](/api/automorph/codec/messagepack/UpickleMessagePackCodec.html) | [automorph-upickle](https://central.sonatype.com/artifact/org.automorph/automorph-upickle_3) | [uPickle](https://github.com/com-lihaoyi/upickle) |[Msg](https://com-lihaoyi.github.io/upickle/#uPack) | [MessagePack](https://msgpack.org/) |
| [ArgonautJsonCodec](/api/automorph/codec/json/ArgonautJsonCodec.html) | [automorph-argonaut](https://central.sonatype.com/artifact/org.automorph/automorph-argonaut_3) | [Argonaut](http://argonaut.io/doc/) |[Json](http://argonaut.io/scaladocs/#argonaut.Json) | [JSON](https://www.json.org/) |


## [Client transport](/api/automorph/spi/ClientTransport.html)

| Class | Artifact | Library | Protocol |
| --- | --- | --- | --- |
| [HttpClient](/api/automorph/transport/http/client/HttpClient.html) (*Default*) | [automorph-core](https://central.sonatype.com/artifact/org.automorph/automorph-core_3) | [Standard](https://docs.oracle.com/en/java/javase/11/docs/api/java.net.http/java/net/http/HttpClient.html) | [HTTP](https://en.wikipedia.org/wiki/HTTP), [WebSocket](https://en.wikipedia.org/wiki/WebSocket) |
| [JettyClient](/api/automorph/transport/http/client/JettyClient.html) | [automorph-jetty](https://central.sonatype.com/artifact/org.automorph/automorph-jetty_3) | [Jetty](https://www.eclipse.org/jetty/) | [HTTP](https://en.wikipedia.org/wiki/HTTP), [WebSocket](https://en.wikipedia.org/wiki/WebSocket) |
| [UrlClient](/api/automorph/transport/http/client/UrlClient.html) | [automorph-core](https://central.sonatype.com/artifact/org.automorph/automorph-core_3) | [Standard](https://docs.oracle.com/javase/8/docs/api/java/net/HttpURLConnection.html) | [HTTP](https://en.wikipedia.org/wiki/HTTP) |
| [RabbitMqClient](/api/automorph/transport/amqp/client/RabbitMqClient.html) | [automorph-rabbitmq](https://central.sonatype.com/artifact/org.automorph/automorph-rabbitmq_3) | [RabbitMQ](https://www.rabbitmq.com/java-client.html) | [AMQP](https://en.wikipedia.org/wiki/Advanced_Message_Queuing_Protocol) |
| [SttpClient](/api/automorph/transport/http/client/SttpClient.html)| [automorph-sttp](https://central.sonatype.com/artifact/org.automorph/automorph-sttp_3) | [STTP](https://sttp.softwaremill.com/en/latest/) | |
| -> | [automorph-sttp](https://central.sonatype.com/artifact/org.automorph/automorph-sttp_3) | [Armeria](https://sttp.softwaremill.com/en/latest/backends/summary.html)| [HTTP](https://en.wikipedia.org/wiki/HTTP) |
| -> | [automorph-sttp](https://central.sonatype.com/artifact/org.automorph/automorph-sttp_3) | [AsyncHttpClient](https://sttp.softwaremill.com/en/latest/backends/summary.html)| [HTTP](https://en.wikipedia.org/wiki/HTTP), [WebSocket](https://en.wikipedia.org/wiki/WebSocket) |
| -> | [automorph-sttp](https://central.sonatype.com/artifact/org.automorph/automorph-sttp_3) | [HttpClient](https://sttp.softwaremill.com/en/latest/backends/summary.html)| [HTTP](https://en.wikipedia.org/wiki/HTTP ), [WebSocket](https://en.wikipedia.org/wiki/WebSocket) |
| [LocalClient](/api/automorph/transport/local/client/LocalClient.html) | [automorph-core](https://central.sonatype.com/artifact/org.automorph/automorph-core_3) | [Standard](https://docs.oracle.com/javase/8/docs/api/java/net/HttpURLConnection.html) |  |


## [Server transport](/api/automorph/spi/ServerTransport.html)

| Class | Artifact | Library | Protocol |
| --- | --- | --- |
| [UndertowServer](/api/automorph/transport/http/server/UndertowServer.html) (*Default*) | [automorph-undertow](https://central.sonatype.com/artifact/org.automorph/automorph-undertow_3) | [Undertow](https://undertow.io/) | [HTTP](https://en.wikipedia.org/wiki/HTTP), [WebSocket](https://en.wikipedia.org/wiki/WebSocket) |
| [VertxServer](/api/automorph/transport/http/server/VertxServer.html) | [automorph-vertx](https://central.sonatype.com/artifact/org.automorph/automorph-vertx_3) | [Vert.x](https://vertx.io/) | [HTTP](https://en.wikipedia.org/wiki/HTTP), [WebSocket](https://en.wikipedia.org/wiki/WebSocket) |
| [JettyServer](/api/automorph/transport/http/server/JettyServer.html) | [automorph-jetty](https://central.sonatype.com/artifact/org.automorph/automorph-jetty_3) | [Jetty](https://www.eclipse.org/jetty/) | [HTTP](https://en.wikipedia.org/wiki/HTTP), [WebSocket](https://en.wikipedia.org/wiki/WebSocket) |
| [AkkaServer](/api/automorph/transport/http/server/AkkaServer.html) | [automorph-akka-http](https://central.sonatype.com/artifact/org.automorph/automorph-akka-http_3) | [Akka HTTP](https://doc.akka.io/docs/akka-http/current/) | [HTTP](https://en.wikipedia.org/wiki/HTTP) |
| [NanoServer](/api/automorph/transport/http/server/NanoServer.html) | [automorph-core](https://central.sonatype.com/artifact/org.automorph/automorph-core_3) | [NanoHTTPD](https://github.com/NanoHttpd/nanohttpd) | [HTTP](https://en.wikipedia.org/wiki/HTTP), [WebSocket](https://en.wikipedia.org/wiki/WebSocket) |
| [RabbitMqServer](/api/automorph/transport/amqp/server/RabbitMqServer.html) | [automorph-rabbitmq](https://central.sonatype.com/artifact/org.automorph/automorph-rabbitmq_3) | [RabbitMq](https://www.rabbitmq.com/java-client.html) | [AMQP](https://en.wikipedia.org/wiki/Advanced_Message_Queuing_Protocol) |


## [Endpoint transport](/api/automorph/spi/EndpointTransport.html)

| Class | Artifact | Library | Protocol |
| --- | --- | --- |
| [UndertowHttpEndpoint](/api/automorph/transport/http/endpoint/UndertowHttpEndpoint.html) | [automorph-undertow](https://central.sonatype.com/artifact/org.automorph/automorph-undertow_3) | [Undertow](https://undertow.io/) | [HTTP](https://en.wikipedia.org/wiki/HTTP) |
| [UndertowWebSocketEndpoint](/api/automorph/transport/websocket/endpoint/UndertowWebSocketEndpoint.html) | [automorph-undertow](https://central.sonatype.com/artifact/org.automorph/automorph-undertow_3) | [Undertow](https://undertow.io/) | [WebSocket](https://en.wikipedia.org/wiki/WebSocket) |
| [VertxHttpEndpoint](/api/automorph/transport/http/endpoint/VertxHttpEndpoint.html) | [automorph-vertx](https://central.sonatype.com/artifact/org.automorph/automorph-vertx_3) | [Vert.x](https://vertx.io/) | [HTTP](https://en.wikipedia.org/wiki/HTTP) |
| [VertxWebSocketEndpoint](/api/automorph/transport/websocket/endpoint/VertxWebSocketEndpoint.html) | [automorph-vertx](https://central.sonatype.com/artifact/org.automorph/automorph-vertx_3) | [Vert.x](https://vertx.io/) | [WebSocket](https://en.wikipedia.org/wiki/WebSocket) |
| [JettyHttpEndpoint](/api/automorph/transport/http/endpoint/JettyHttpEndpoint.html) | [automorph-jetty](https://central.sonatype.com/artifact/org.automorph/automorph-jetty_3) | [Jetty](https://www.eclipse.org/jetty/) | [HTTP](https://en.wikipedia.org/wiki/HTTP) |
| [JettyWebSocketEndpoint](/api/automorph/transport/http/endpoint/JettyWebSocketEndpoint.html) | [automorph-jetty](https://central.sonatype.com/artifact/org.automorph/automorph-jetty_3) | [Jetty](https://www.eclipse.org/jetty/) | [HTTP](https://en.wikipedia.org/wiki/WebSocket) |
| [AkkaHttpEndpoint](/api/automorph/transport/http/endpoint/AkkaHttpEndpoint.html) | [automorph-akka-http](https://central.sonatype.com/artifact/org.automorph/automorph-akka-http_3) | [Akka HTTP](https://doc.akka.io/docs/akka-http/current/) | [HTTP](https://en.wikipedia.org/wiki/HTTP) |
| [FinagleEndpoint](/api/automorph/transport/http/endpoint/FinagleHttpEndpoint.html) | [automorph-finagle](https://central.sonatype.com/artifact/org.automorph/automorph-finagle_3) | [Finagle](https://twitter.github.io/finagle/) | [HTTP](https://en.wikipedia.org/wiki/HTTP) |
| [TapirHttpEndpoint](/api/automorph/transport/http/endpoint/TapirHttpEndpoint.html) | [automorph-tapir](https://central.sonatype.com/artifact/org.automorph/automorph-tapir_3) | [Tapir](https://tapir.softwaremill.com/) | [WebSocket](https://en.wikipedia.org/wiki/WebSocket) |
| -> | [automorph-tapir](https://central.sonatype.com/artifact/org.automorph/automorph-tapir_3) | [Armeria](https://tapir.softwaremill.com/en/latest/server/armeria.html)| [HTTP](https://en.wikipedia.org/wiki/HTTP) |
| -> | [automorph-tapir](https://central.sonatype.com/artifact/org.automorph/automorph-tapir_3) | [Vert.x](https://tapir.softwaremill.com/en/latest/server/vertx.html)| [HTTP](https://en.wikipedia.org/wiki/HTTP) |
| -> | [automorph-tapir](https://central.sonatype.com/artifact/org.automorph/automorph-tapir_3) | [Http4s](https://tapir.softwaremill.com/en/latest/server/http4s.html)| [HTTP](https://en.wikipedia.org/wiki/HTTP) |
| -> | [automorph-tapir](https://central.sonatype.com/artifact/org.automorph/automorph-tapir_3) | [Netty](https://tapir.softwaremill.com/en/latest/server/netty.html)| [HTTP](https://en.wikipedia.org/wiki/HTTP) |
| [LocalEndpoint](/api/automorph/transport/local/endpoint/LocalEndpoint.html) | [automorph-core](https://central.sonatype.com/artifact/org.automorph/automorph-core_3) |  |  |

