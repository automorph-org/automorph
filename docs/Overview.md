---
sidebar_position: 1
---

# Overview

**Automorph** is a Scala [RPC](https://en.wikipedia.org/wiki/Remote_procedure_call) client and server library
for calling and serving remote APIs in a few lines of code.
```
### Goals

* Enable efficient calling and serving of **remote APIs** with **no boilerplate**
* Support manipulation of **transport protocol metadata** and **dynamic message payload**
* Facilitate **easy integration** by **preserving technical decisions** of existing applications

### Main interface example

```scala
// Expose a server API implementation to be called remotely
val apiServer = server.bind(api)

// Create a type-safe local proxy for the remote API from an API trait
val remoteApi = client.bind[Api]


## Features

### Client

  * Transparently generate optimized [RPC client](Quickstart#static-client) bindings at compile time.
  * Call remote APIs using a supported transport protocol by selecting a [client transport](Plugins#client-transport) layer.
  * Change the mapping of [local to remote RPC function names](Examples#client-function-names).
  * Change the mapping of [RPC errors to exceptions](Examples#client-error-mapping).

### Server

  * Transparently generate optimized [RPC server](Quickstart#server) bindings at compile time.
  * Serve remote APIs using a standalone server by selecting a [server transport](Plugins#server-transport) layer.
  * Embed remote API into an existing server via a suitable [endpoint transport](Plugins#endpoint-transport).
  * Automatically expose RPC API functions providing [OpenRPC](https://spec.open-rpc.org) and [OpenAPI](https://github.com/OAI/OpenAPI-Specification) schemas.
  * Change the mapping of called to implemented RPC function names on [server side](Examples#server-function-names).
  * Change the mapping of exceptions to RPC errors [client side](Examples#server-error-mapping).

### General

  * Use [JSON-RPC](https://www.jsonrpc.org/specification) or [Web-RPC](Web-RPC) as an [RPC protocol](Plugins#rpc-protocol).
  * Use any [effect system](Plugins#effect-system) to call or implement remote APIs.
  * Serialize arbitrary data types by configuring the selected [message codec](Examples#data-serialization).
  * Use optional remote API extensions to access or modify [transport protocol metadata](Examples#metadata).
  * Use RPC protocol message model to create and consume [dynamic message payload](Examples#dynamic-payload).



## Usage

### Platform requirements

* [Scala](https://www.scala-lang.org/) 3.3+ or 2.13+
* [Java Runtime Environment](https://openjdk.java.net/) 11+
* [SLF4J](http://www.slf4j.org/) logger implementation (optional)


### [API](https://automorph.org/api/automorph.html)

Following classes represent the primary entry points to Automorph functionality:

* [RPC client](https://automorph.org/api/automorph/RpcClient.html) - Used to perform type-safe remote API calls or send one-way messages.
* [RPC server](https://automorph.org/api/automorph/RpcServer.html) - Used to serve remote API requests and invoke bound API methods to process them.
* [RPC endpoint](https://automorph.org/api/automorph/RpcEndpoint.html) - Used to handle remote API requests as part of an existing server
and invoke bound API methods to process them.

Various combinations of [RPC protocol](https://automorph.org/docs/Plugins#rpc-protocol), [effect system](https://automorph.org/docs/Plugins#effect-system),
[message codec](https://automorph.org/docs/Plugins#message-codec) and [transport layer](https://automorph.org/docs/Plugins#transport-layer) can be utilized by
supplying the desired plugin instances to the factory methods of the primary classes listed above.

There are also additional [factory methods](https://automorph.org/api/automorph/Default$.html) for
creating primary class instances with [default plugins](https://automorph.org/docs/Plugins#default-plugins).


### [SPI](https://automorph.org/api/automorph/spi.html)

Following traits define interfaces for implementing various Automorph [plugins](https://automorph.org/docs/Plugins):

* [RPC protocol](https://automorph.org/api/automorph/spi/RpcProtocol.html) -
Enables use of a specific RPC protocol.
* [Effect system](https://automorph.org/api/automorph/spi/EffectSystem.html) - 
Enables remote APIs to use specific effect handling abstraction.
* [Message codec](https://automorph.org/api/automorph/spi/MessageCodec.html) -
Enables serialization of RPC messages into specific structured data format.
* [Client transport](https://automorph.org/api/automorph/spi/ClientTransport.html) -
Enables RPC client to send requests and receive responses using specific transport protocol.
* [Server transport](https://automorph.org/api/automorph/spi/ServerTransport.html) -
Enables RPC server to receive requests and send responses using specific transport protocol.
* [Endpoint transport](https://automorph.org/api/automorph/spi/EndpointTransport.html) -
Enables RPC endpoint to integrate with and handle requests from an existing server infrastructure.


### Limitations

* Remote APIs must not contain [overloaded methods](https://en.wikipedia.org/wiki/Function_overloading)
* Remote API methods must not use [type parameters](https://docs.scala-lang.org/tour/polymorphic-methods.html)
* Remote API methods must not be [inline](https://docs.scala-lang.org/scala3/guides/macros/inline.html)
* Remote APIs must not be used from within the [App](https://scala-lang.org/api/3.x/scala/App.html) trait nor from within any other [delayed initialization](https://scala-lang.org/api/3.x/scala/DelayedInit.html) scope
* JSON-RPC protocol implementation does not support [batch requests](https://www.jsonrpc.org/specification#batch)
* Maximum number of arguments the RPC client supports for remote APIs calls without API trait is 9
* RPC protocol plugin constructors for Scala 2 might require explicitly supplied type parameters due to [type inference](https://docs.scala-lang.org/tour/type-inference.html) constraints


### Known issues

* Mangled signatures for a few nonessential methods in Automorph API documentation caused by a Scaladoc defect


## Supported standards

Following technical standards are supported by freely combining the relevant
[plugins](https://automorph.org/docs/Plugins).

### RPC protocols

* [JSON-RPC](https://www.jsonrpc.org/specification) (*Default*)
* [Web-RPC](https://automorph.org/docs/Web-RPC)

### Transport protocols

* [HTTP](https://en.wikipedia.org/wiki/Hypertext_Transfer_Protocol) (*Default*)
* [WebSocket](https://en.wikipedia.org/wiki/WebSocket)
* [AMQP](https://en.wikipedia.org/wiki/Advanced_Message_Queuing_Protocol)

### Message formats

* [JSON](https://www.json.org) (*Default*)
* [MessagePack](https://msgpack.org)

### Effect Handling

* [Asynchronous](https://docs.scala-lang.org/overviews/core/futures.html) (*Default*)
* [Synchronous](https://docs.scala-lang.org/scala3/book/taste-functions.html)
* [Monadic](https://blog.softwaremill.com/figuring-out-scala-functional-programming-libraries-af8230efccb4)

### API schemas

* [OpenRPC](https://spec.open-rpc.org)
* [OpenAPI](https://github.com/OAI/OpenAPI-Specification)


## Author

* Martin Ockajak


### Special thanks

* Luigi Antognini


### Inspired by

* [Scala JSON-RPC](https://github.com/shogowada/scala-json-rpc)
* [Autowire](https://github.com/lihaoyi/autowire)
* [Tapir](https://tapir.softwaremill.com)
* [STTP](https://sttp.softwaremill.com)
* [ZIO](https://zio.dev)
