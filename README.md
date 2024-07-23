![Automorph](https://github.com/automorph-org/automorph/raw/main/site/static/banner.jpg)

[![Documentation](https://img.shields.io/badge/Website-documentation-blue)](https://automorph.org)
[![API](https://img.shields.io/badge/Scaladoc-API-mediumpurple)](https://automorph.org/api/automorph.html)
[![Artifacts](https://img.shields.io/maven-central/v/org.automorph/automorph-default_3?label=Artifacts)](
https://central.sonatype.com/namespace/org.automorph)
[![Scala](https://img.shields.io/badge/Scala-2.13%20%2F%203.3-goldenrod)](https://www.scala-lang.org)
[![License](https://img.shields.io/github/license/automorph-org/automorph?label=License&color=teal)](https://github.com/automorph-org/automorph/blob/main/LICENSE)
[![Build](https://github.com/automorph-org/automorph/actions/workflows/build.yml/badge.svg)](https://github.com/automorph-org/automorph/actions/workflows/build.yml)

**Automorph** is a Scala [RPC](https://en.wikipedia.org/wiki/Remote_procedure_call) client and server library
for type-safely calling and serving remote APIs in a few lines of code.


## Features

- **Seamless** - Generate type-safe [RPC](https://en.wikipedia.org/wiki/Remote_procedure_call) [client](https://automorph.org/docs/Quickstart#static-client) or [server](https://automorph.org/docs/Quickstart#server) bindings from existing public API methods at compile time.
- **Flexible** - Customize [data serialization](https://automorph.org/docs/Examples#data-type-serialization), remote API [function names](https://automorph.org/docs/Examples#client-function-names), RPC protocol [errors](https://automorph.org/docs/Examples#client-exceptions) and [authentication](https://automorph.org/docs/Examples#http-authentication).
- **Modular** - Freely combine [RPC protocol](https://automorph.org/docs/Plugins#rpc-protocol), [message format](https://automorph.org/docs/Plugins#message-codec), [transport protocol](https://automorph.org/docs/Plugins#client-transport) and [effect handling](https://automorph.org/docs/Plugins#effect-system) layers.
- **Permissive** - Consume or create [dynamic message payload](https://automorph.org/docs/Examples#dynamic-payload) and access or modify [transport protocol metadata](https://automorph.org/docs/Examples#metadata).
- **Discoverable** - Utilize discovery functions providing [OpenRPC](https://spec.open-rpc.org) 1.3+ and [OpenAPI](https://www.openapis.org) 3.1+ schemas for exposed APIs.
- **Compatible** - Use with [Scala](https://www.scala-lang.org) 3.3+ or 2.13+ on [JRE](https://openjdk.java.net/) 11+ and easily integrate with various libraries using [plugins](https://automorph.org/docs/Plugins).
- **Standards** - [JSON-RPC](https://www.jsonrpc.org/specification), [Web-RPC](https://automorph.org/docs/Web-RPC), [HTTP](https://en.wikipedia.org/wiki/HTTP), [WebSocket](https://en.wikipedia.org/wiki/WebSocket), [AMQP](https://en.wikipedia.org/wiki/Advanced_Message_Queuing_Protocol), [JSON](https://www.json.org), [MessagePack](https://msgpack.org), [Smile](https://github.com/FasterXML/smile-format-specification), [CBOR](https://cbor.io), [Ion](https://amazon-ion.github.io/ion-docs).
- **Integrations** - [STTP](https://automorph.org/docs/Plugins#client-transport), [Tapir](https://automorph.org/docs/Plugins#endpoint-transport), [Undertow](), [Vert.x](https://automorph.org/docs/Plugins#server-transport), [Jetty](https://automorph.org/docs/Plugins#server-transport), [Finagle](https://automorph.org/docs/Plugins#endpoint-transport), [Akka HTTP](https://automorph.org/docs/Plugins#endpoint-transport), [Pekko HTTP](https://automorph.org/docs/Plugins#endpoint-transport), [RabbitMQ](https://automorph.org/docs/Plugins#client-transport), [Circe](https://automorph.org/docs/Plugins#message-codec), [Jackson](https://automorph.org/docs/Plugins#message-codec), [weePickle](https://automorph.org/docs/Plugins#message-codec), [uPickle](https://automorph.org/docs/Plugins#message-codec), [Argonaut](https://automorph.org/docs/Plugins#message-codec).
- **Effects** - [Identity](https://automorph.org/docs/Plugins#effect-system), [Try](https://automorph.org/docs/Plugins#effect-system), [Future](https://automorph.org/docs/Plugins#effect-system), [ZIO](https://automorph.org/docs/Plugins#effect-system), [Monix](https://automorph.org/docs/Plugins#effect-system), [Cats Effect](https://automorph.org/docs/Plugins#effect-system), [Scalaz Effect](https://automorph.org/docs/Plugins#effect-system).


## Example

```scala
// Define a remote API
trait Api:
  def hello(n: Int): Future[String]

// Create server implementation of the remote API
val service = new Api:
  def hello(n: Int): Future[String] = Future(s"Hello world $n")

// Register the API implementation to be available as a remote service
val apiServer = server.service(service)

// Create a type-safe local proxy for the remote API from an API trait
val remoteApi = client.proxy[Api]

// Call the remote API function via the local proxy
remoteApi.hello(1)

// Call the remote API function dynamically without using the API trait
client.call[String]("hello")("n" -> 1)
```

*Note*: Mundane parts of the code are omitted and can be found in the [full example](https://automorph.org/docs/Quickstart).


## Links

- [Get Started](https://automorph.org/docs/Quickstart)
- [Documentation](https://automorph.org)
- [API](https://automorph.org/api/automorph.html)
- [Artifacts](https://central.sonatype.com/namespace/org.automorph)
- [Contact](mailto:automorph.org@proton.me)


--

TODO:
- Upgrade Jetty client to version 12
- Upgrade Jetty server to verson 12
- Test reverse call logic
- Merge RPC client and server
- Create standard TypeScript example using Mole RPC
- Create reverse call TypeScript example using Mole RPC
- Fix ZIO HTTP test
- Fix Play test

