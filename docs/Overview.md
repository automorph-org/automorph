---
sidebar_position: 1
---

# Overview

**Automorph** is a Scala [RPC](https://en.wikipedia.org/wiki/Remote_procedure_call) client and server library
for calling and serving remote APIs in a few lines of code.

## Goals

- Provide efficient **calling** and **serving** of **remote APIs**
- Ensure there is **no boilerplate** API code and **minimal dependencies** are required
- Support manipulation of **transport protocol metadata** and **dynamic message payload**
- Facilitate **smooth integration** by supporting wide range of **existing technology**
- Allow for easy **customization** and **extension** of library features


## Features

### Client

- Transparently generates optimized [RPC client](https://automorph.org/docs/Quickstart#static-client) bindings at compile time.
- Calls remote APIs using a supported transport protocol by selecting a [client transport](https://automorph.org/docs/Plugins#client-transport) layer.
- Allows changing the [local to remote RPC function names mapping](https://automorph.org/docs/Examples#client-function-names).
- Allows changing the [RPC errors to exceptions mapping](https://automorph.org/docs/Examples#client-error-mapping).

### Server

- Transparently generates optimized [RPC server](https://automorph.org/docs/Quickstart#server) bindings at compile time.
- Serves remote APIs using a standalone server by selecting a [server transport](https://automorph.org/docs/Plugins#server-transport) layer.
- Embeds remote API into an existing server via a suitable [endpoint transport](https://automorph.org/docs/Plugins#endpoint-transport).
- Automatically generates RPC API discovery functions providing [OpenRPC](https://spec.open-rpc.org) 1.3+ and [OpenAPI](https://www.openapis.org) 3.1+ schemas.
- Allows changing the [remote to local RPC function names mapping](https://automorph.org/docs/Examples#server-function-names).
- Allows changing the [exceptions to RPC errors mapping](https://automorph.org/docs/Examples#server-error-mapping).

### General

- Enables flexible builds with specific artifacts for selected [integrations](https://automorph.org/docs/Plugins) only.
- Supports use of [JSON-RPC](https://www.jsonrpc.org/specification) or [Web-RPC](https://automorph.org/docs/Web-RPC) as an [RPC protocol](https://automorph.org/docs/Plugins#rpc-protocol).
- Supports all [effect systems](https://automorph.org/docs/Plugins#effect-system) to call or implement remote APIs.
- Serializes arbitrary data types via the selected [message codec](https://automorph.org/docs/Examples#data-type-serialization).
- Defines an easily composable set of [default plugins](https://automorph.org/docs/Plugins#default-plugins) and configuration values.
- Provides optional remote API extensions to access or modify [transport protocol metadata](https://automorph.org/docs/Examples#metadata).
- Provides RPC protocol message model to create and consume [dynamic message payload](https://automorph.org/docs/Examples#dynamic-payload).



## Usage

### Platform requirements

- [Scala](https://www.scala-lang.org/) 3.3+ or 2.13+
- [Java Runtime Environment](https://openjdk.java.net/) 11+
- [SLF4J](http://www.slf4j.org/) logger implementation (optional)

### Main interface

Define a remote API:
```scala
trait Api:
  def hello(some: String, n: Int): Future[String]
```

Create server implementation of the remote API:
```scala
val service = new Api:
  def hello(some: String, n: Int): Future[String] = Future(s"Hello $some $n!")
```

Expose a server API implementation to be called remotely:
```scala
val apiServer = server.bind(service)
```

Create a type-safe local proxy for the remote API from an API trait:
```scala
val remoteApi = client.bind[Api]
```

Call the remote API function via the local proxy:
```scala
remoteApi.hello("world", 1)
```

Call the remote API function dynamically not using the API trait:
```scala
client.call[String]("hello")("some" -> "world", "n" -> 1)
```

*Note*: Mundane parts of the code are omitted and can be found in the [full example](https://automorph.org/docs/Quickstart).

### [API](https://automorph.org/api/automorph.html)

The following classes represent primary entry points to Automorph functionality:

- [RPC client](https://automorph.org/api/automorph/RpcClient.html) - Used to perform type-safe remote API calls or send one-way messages.
- [RPC server](https://automorph.org/api/automorph/RpcServer.html) - Used to serve remote API requests and invoke bound API methods to process them.
- [RPC endpoint](https://automorph.org/api/automorph/RpcEndpoint.html) - Used to handle remote API requests as part of an existing server
and invoke bound API methods to process them.

Various combinations of [RPC protocol](https://automorph.org/docs/Plugins#rpc-protocol), [effect system](https://automorph.org/docs/Plugins#effect-system),
[message codec](https://automorph.org/docs/Plugins#message-codec) and [transport layer](https://automorph.org/docs/Plugins#transport-layer) can be utilized by
supplying the desired plugin instances to the factory methods of the primary classes listed above.

There are also additional [factory methods](https://automorph.org/api/automorph/Default$.html) for
creating primary class instances with [default plugins](https://automorph.org/docs/Plugins#default-plugins).


### [SPI](https://automorph.org/api/automorph/spi.html)

The following traits define interfaces for implementing various Automorph [plugins](https://automorph.org/docs/Plugins):

- [RPC protocol](https://automorph.org/api/automorph/spi/RpcProtocol.html) -
Enables use of a specific RPC protocol.
- [Effect system](https://automorph.org/api/automorph/spi/EffectSystem.html) - 
Enables remote APIs to use specific effect handling abstraction.
- [Message codec](https://automorph.org/api/automorph/spi/MessageCodec.html) -
Enables serialization of RPC messages into specific structured data format.
- [Client transport](https://automorph.org/api/automorph/spi/ClientTransport.html) -
Enables RPC client to send requests and receive responses using specific transport protocol.
- [Server transport](https://automorph.org/api/automorph/spi/ServerTransport.html) -
Enables RPC server to receive requests and send responses using specific transport protocol.
- [Endpoint transport](https://automorph.org/api/automorph/spi/EndpointTransport.html) -
Enables RPC endpoint to integrate with and handle requests from an existing server infrastructure.


### Limitations

- Remote APIs must not contain [overloaded methods](https://en.wikipedia.org/wiki/Function_overloading)
- Remote API methods must not use [type parameters](https://docs.scala-lang.org/tour/polymorphic-methods.html)
- Remote API methods must not be [inline](https://docs.scala-lang.org/scala3/guides/macros/inline.html)
- Remote APIs must not be used from within the [App](https://scala-lang.org/api/3.x/scala/App.html) trait nor from within any other [delayed initialization](https://scala-lang.org/api/3.x/scala/DelayedInit.html) scope
- JSON-RPC protocol implementation does not support [batch requests](https://www.jsonrpc.org/specification#batch)
- Maximum number of arguments the RPC client supports for [dynamic remote APIs calls](https://automorph.org/docs/Quickstart#dynamic-client) not using an API trait is 9
- RPC protocol plugin constructors for Scala 2 might require explicitly supplied type parameters due to [type inference](https://docs.scala-lang.org/tour/type-inference.html) constraints


### Known issues

- Mangled signatures of a few nonessential methods in the API documentation caused by a Scaladoc defect


## Supported standards

The following technical standards are supported by freely combining appropriate
[plugins](https://automorph.org/docs/Plugins).

### RPC protocols

- [JSON-RPC](https://www.jsonrpc.org/specification) (*Default*)
- [Web-RPC](https://automorph.org/docs/Web-RPC)

### Transport protocols

- [HTTP](https://en.wikipedia.org/wiki/Hypertext_Transfer_Protocol) (*Default*)
- [WebSocket](https://en.wikipedia.org/wiki/WebSocket)
- [AMQP](https://en.wikipedia.org/wiki/Advanced_Message_Queuing_Protocol)

### Message formats

- [JSON](https://www.json.org) (*Default*)
- [MessagePack](https://msgpack.org)

### Effect handling

- [Asynchronous](https://docs.scala-lang.org/overviews/core/futures.html) (*Default*)
- [Synchronous](https://docs.scala-lang.org/scala3/book/taste-functions.html)
- [Monadic](https://blog.softwaremill.com/figuring-out-scala-functional-programming-libraries-af8230efccb4)

### API schemas

- [OpenRPC](https://spec.open-rpc.org)
- [OpenAPI](https://www.openapis.org)


## Author

- Martin Ockajak


### Special thanks

- Luigi Antognini


### Inspired by

- [Scala JSON-RPC](https://github.com/shogowada/scala-json-rpc)
- [Autowire](https://github.com/lihaoyi/autowire)
- [Tapir](https://tapir.softwaremill.com)
- [STTP](https://sttp.softwaremill.com)
- [ZIO](https://zio.dev)
