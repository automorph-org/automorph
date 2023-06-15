---
sidebar_position: 1
---

# Overview

**Automorph** is an [RPC](https://en.wikipedia.org/wiki/Remote_procedure_call) client and server library for
[Scala](https://www.scala-lang.org/) providing an easy way to invoke and expose remote APIs using
[JSON-RPC](https://www.jsonrpc.org/specification) and [Web-RPC](Web-RPC) protocols.


## Goals

* Enable **remote access** to **APIs** while **automatically creating the intermediate layer**
* Support managing **dynamic message payload** and accessing **transport protocol metadata**
* Provide **smooth integration** with existing **applications** and related **libraries**


## Requirements

* [Scala](https://www.scala-lang.org/) 3.2+ or 2.13+
* [Java Runtime Environment](https://openjdk.java.net/) 11+
* [SLF4J](http://www.slf4j.org/) logger implementation (optional)


## [API](https://en.wikipedia.org/wiki/API)

Entry points for the application logic to invoke or expose remote APIs:

* [RPC client](/api/automorph/RpcClient.html) - invoke remote APIs
* [RPC server](/api/automorph/RpcServer.html) - serve APIs as remote
* [RPC endpoint](/api/automorph/RpcEndpoint.html) - expose APIs as remote within an existing server


## [SPI](https://en.wikipedia.org/wiki/Service_provider_interface)

Traits for implementation of various integration plugins:

* [Effect system](/api/automorph/spi/EffectSystem.html) - accessing remote APIs using effect handling abstractions
* [Message codec](/api/automorph/spi/MessageCodec.html) - serialization of RPC messages into structured data formats
* [Client transport](/api/automorph/spi/ClientTransport.html) - tranmitting messages for RPC clients
* [Server transport](/api/automorph/spi/ServerTransport.html) - transmitting messages for RPC servers
* [Endpoint transport](/api/automorph/spi/EndpointTransport.html) - adding RPC support to existing servers
* [RPC protocol](/api/automorph/spi/RpcProtocol.html) - specific RPC protocol implementations


## Limitations

* Remote APIs must not contain [overloaded methods](https://en.wikipedia.org/wiki/Function_overloading)
* Remote API methods must not use [type parameters](https://docs.scala-lang.org/tour/polymorphic-methods.html)
* Remote API methods must not be [inline](https://docs.scala-lang.org/scala3/guides/macros/inline.html)
* Remote APIs must not be used from within the [App](https://scala-lang.org/api/3.x/scala/App.html) trait nor from
within any other [delayed initialization](https://scala-lang.org/api/3.x/scala/DelayedInit.html) scope
* JSON-RPC protocol implementation does not support batch requests
* Due to Scala 2 [type inference](https://docs.scala-lang.org/tour/type-inference.html) constraints it may be necessary
to explicitly supply type parameters when creating RPC protocol plugin instances


## Known issues

* Mangled Scaladoc signatures for a few nonessential methods
* Missing Monix effect system plugin in Scaladoc index


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

