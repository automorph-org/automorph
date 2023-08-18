---
sidebar_position: 1
---

# Overview

**Automorph** is a Scala [RPC](https://en.wikipedia.org/wiki/Remote_procedure_call) client and server library
for invoking and exposing remote APIs in a few lines of code.


## Design goals

* Enable **remote API calls** with **no boilerplate** and **minimal limitations**
* Ensure **no technical changes** are required for **existing projects**
* Facilitate **customization** and **extension** of library features
* Allow use of **dynamic message payload** in remote API calls
* Allow access to **transport protocol metadata** for remote API calls


## Platform requirements

* [Scala](https://www.scala-lang.org/) 3.3+ or 2.13+
* [Java Runtime Environment](https://openjdk.java.net/) 11+
* [SLF4J](http://www.slf4j.org/) logger implementation (optional)


## [API](https://automorph.org/api/automorph.html)

Entry points for the application logic to invoke or expose remote APIs:

* [RPC client](https://automorph.org/api/automorph/RpcClient.html) - invoke remote APIs
* [RPC server](https://automorph.org/api/automorph/RpcServer.html) - serve APIs as remote
* [RPC endpoint](https://automorph.org/api/automorph/RpcEndpoint.html) - expose APIs as remote within an existing server


## [SPI](https://automorph.org/api/automorph/spi.html)

Traits for implementation of various integration plugins:

* [Effect system](https://automorph.org/api/automorph/spi/EffectSystem.html) - accessing remote APIs using effect handling abstractions
* [Message codec](https://automorph.org/api/automorph/spi/MessageCodec.html) - serialization of RPC messages into structured data formats
* [Client transport](https://automorph.org/api/automorph/spi/ClientTransport.html) - tranmitting messages for RPC clients
* [Server transport](https://automorph.org/api/automorph/spi/ServerTransport.html) - transmitting messages for RPC servers
* [Endpoint transport](https://automorph.org/api/automorph/spi/EndpointTransport.html) - adding RPC support to existing servers
* [RPC protocol](https://automorph.org/api/automorph/spi/RpcProtocol.html) - specific RPC protocol implementations


## Limitations

* Remote APIs must not contain [overloaded methods](https://en.wikipedia.org/wiki/Function_overloading)
* Remote API methods must not use [type parameters](https://docs.scala-lang.org/tour/polymorphic-methods.html)
* Remote API methods must not be [inline](https://docs.scala-lang.org/scala3/guides/macros/inline.html)
* Remote APIs must not be used from within the [App](https://scala-lang.org/api/3.x/scala/App.html) trait nor from within any other [delayed initialization](https://scala-lang.org/api/3.x/scala/DelayedInit.html) scope
* Maximum number of supplied arguments when invoking remote API functions dynamically from the client is 7
* JSON-RPC protocol implementation does not support [batch requests](https://www.jsonrpc.org/specification#batch)
* RPC protocol plugin instantiation may require specifying the type parameters explicitly due to Scala 2 [type inference](https://docs.scala-lang.org/tour/type-inference.html) constraints


## Known issues

* Mangled signatures for a few nonessential methods in Automorph API documentation caused by a Scaladoc defect


## Supported standards

### RPC protocols

* [JSON-RPC](https://www.jsonrpc.org/specification) (*Default*)
* [Web-RPC](Web-RPC)

### Transport protocols

* [HTTP](https://en.wikipedia.org/wiki/Hypertext_Transfer_Protocol) (*Default*)
* [WebSocket](https://en.wikipedia.org/wiki/WebSocket)
* [AMQP](https://en.wikipedia.org/wiki/Advanced_Message_Queuing_Protocol)

### Message formats

* [JSON](https://www.json.org) (*Default*)
* [MessagePack](https://msgpack.org)

### Effect Handling

* [Synchronous](https://docs.scala-lang.org/scala3/book/taste-functions.html) (*Default*)
* [Asynchronous](https://docs.scala-lang.org/overviews/core/futures.html) (*Default*)
* [Monadic](https://blog.softwaremill.com/figuring-out-scala-functional-programming-libraries-af8230efccb4)

### API schemas

* [OpenRPC](https://spec.open-rpc.org)
* [OpenAPI](https://github.com/OAI/OpenAPI-Specification)


## Author

* Martin Ockajak


## Special thanks to

* Luigi Antognini


## Inspired by

* [Scala JSON-RPC](https://github.com/shogowada/scala-json-rpc)
* [Autowire](https://github.com/lihaoyi/autowire)
* [Tapir](https://tapir.softwaremill.com)
* [STTP](https://sttp.softwaremill.com)
* [ZIO](https://zio.dev)
