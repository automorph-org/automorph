<br>

![Automorph](https://github.com/automorph-org/automorph/raw/main/site/static/banner.jpg)

[![Documentation](https://img.shields.io/badge/Website-documentation-blue)](https://automorph.org)
[![API](https://img.shields.io/badge/Scaladoc-API-purple)](https://automorph.org/api/automorph.html)
[![Artifacts](https://img.shields.io/maven-central/v/org.automorph/automorph-default_3?label=Artifacts)](
https://central.sonatype.com/namespace/org.automorph)
[![Scala](https://img.shields.io/badge/Scala-2.13%2F3.3-yellow)](https://www.scala-lang.org)
[![Build](https://github.com/automorph-org/automorph/actions/workflows/build.yml/badge.svg)](https://github.com/automorph-org/automorph/actions/workflows/build.yml)

**Automorph** is a Scala [RPC](https://en.wikipedia.org/wiki/Remote_procedure_call) client and server library
for invoking and exposing remote APIs in a few lines of code.

---

**Main interface**

```scala
// Define a remote API
trait Api:
  def hello(n: Int): Future[String]

// Create server implementation of the remote API
val service = new Api:
  def hello(n: Int): Future[String] = Future(s"Hello world $n")

// Expose a server API implementation to be called remotely
val apiServer = server.bind(service)

// Create a type-safe local proxy for the remote API from an API trait
val remoteApi = client.bind[Api]

// Call the remote API function via the local proxy
remoteApi.hello(1)

// Call the remote API function dynamically not using the API trait
client.call[String]("hello")("n" -> 1)
```

*Note*: Mundane parts of the code are omitted and can be found in the [full example](https://automorph.org/docs/Quickstart).

---

- **Seamless** - Generate optimized [RPC](https://en.wikipedia.org/wiki/Remote_procedure_call) [client](https://automorph.org/docs/Quickstart#static-client) or [server](https://automorph.org/docs/Quickstart#server) bindings from existing public API methods at compile time.
- **Flexible** - Customize [data serialization](https://automorph.org/docs/Examples#data-type-serialization), remote API [function names](https://automorph.org/docs/Examples#client-function-names), RPC protocol [errors](https://automorph.org/docs/Examples#client-exceptions) and [authentication](https://automorph.org/docs/Examples#http-authentication).
- **Modular** - Choose plugins for [RPC protocol](https://automorph.org/docs/Plugins#rpc-protocol), [effect handling](https://automorph.org/docs/Plugins#effect-system), [transport protocol](https://automorph.org/docs/Plugins#client-transport) and [message format](https://automorph.org/docs/Plugins#message-codec).
- **Permissive** - Consume or create [dynamic message payload](https://automorph.org/docs/Examples#dynamic-payload) and access or modify [transport protocol metadata](https://automorph.org/docs/Examples#metadata).
- **Discoverable** - Utilize discovery functions providing [OpenRPC](https://spec.open-rpc.org) 1.3+ and [OpenAPI](https://www.openapis.org) 3.1+ schemas for exposed APIs.
- **Compatible** - Use with [Scala](https://www.scala-lang.org) 3.3+ or 2.13+ on [JRE](https://openjdk.java.net/) 11+ and easily integrate with various popular [libraries](https://automorph.org/docs/Plugins).
- **RPC protocols** - [JSON-RPC](https://www.jsonrpc.org/specification), [Web-RPC](https://automorph.org/docs/Web-RPC).
- **Transport protocols** - [HTTP](https://automorph.org/docs/Examples#http-authentication), [WebSocket](https://automorph.org/docs/Examples#websocket-transport), [AMQP](https://automorph.org/docs/Examples#amqp-transport).
- **Effect handling** - [Synchronous](https://automorph.org/docs/Examples#synchronous-call), [Asynchronous](https://automorph.org/docs/Examples#asynchronous-call), [Monadic](https://automorph.org/docs/Examples#effect-system).

---

- [Get Started](https://automorph.org/docs/Quickstart)
- [Documentation](https://automorph.org)
- [API](https://automorph.org/api/automorph.html)
- [Artifacts](https://central.sonatype.com/namespace/org.automorph)
- [Contact](mailto:automorph.org@proton.me)

