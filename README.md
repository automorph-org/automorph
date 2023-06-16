<br>

![automorph](https://github.com/automorph-org/automorph/raw/main/site/static/banner.jpg)

[![Documentation](https://img.shields.io/badge/Website-Documentation-purple)](https://automorph.org)
[![API](https://img.shields.io/badge/Scaladoc-API-blue)](https://automorph.org/api/index.html)
[![Artifacts](https://img.shields.io/badge/Releases-Artifacts-yellow)](
https://central.sonatype.com/namespace/org.automorph)
[![Build](https://github.com/automorph-org/automorph/workflows/Build/badge.svg)](
https://github.com/automorph-org/automorph/actions/workflows/build.yml)



# Overview

**Automorph** is a Scala [RPC](https://en.wikipedia.org/wiki/Remote_procedure_call) client and server library
for effortlessly invoking and exposing remote APIs.

* **Seamless** - Generate optimized [RPC](https://en.wikipedia.org/wiki/Remote_procedure_call) [client](docs/Quickstart#static-client) or [server](docs/Quickstart#server) bindings from existing public API methods at compile time.
* **Flexible** - Customize [data serialization](docs/Examples#data-serialization), [remote API function names](docs/Examples#client-function-names), [RPC protocol errors](docs/Examples#client-exceptions) and [authentication](docs/Examples#http-authentication).
* **Modular** - Choose plugins for [RPC protocol](docs/Plugins#rpc-protocol), [effect handling](docs/Plugins#effect-system), [transport protocol](docs/Plugins#message-transport) and [message format](docs/Plugins#message-codec).
* **Permissive** - Manage [dynamic message payload](docs/Examples#dynamic-payload) and transparently access [transport protocol metadata](docs/Examples#metadata).
* **Discoverable** - Consume and provide [OpenRPC](https://spec.open-rpc.org) 1.3+ or [OpenAPI](https://github.com/OAI/OpenAPI-Specification) 3.1+ schemas using API discovery functions.
* **Compatible** - Supports [Scala](https://www.scala-lang.org) 3.3+ and 2.13+ on [JRE](https://openjdk.java.net/) 11+ and integrates with various popular [libraries](docs/Plugins).
* **RPC protocols** - [JSON-RPC](https://www.jsonrpc.org/specification), [Web-RPC](docs/Web-RPC).
* **Transport protocols** - [HTTP](docs/Examples#http-authentication), [WebSocket](docs/Examples#websocket-transport), [AMQP](docs/Examples#amqp-transport).
* **Effect handling** - [Synchronous](docs/Examples#synchronous-call), [Asynchronous](docs/Examples#asynchronous-call), [Monadic](docs/Examples#effect-system).


# Learn

* [Quick Start](https://automorph.org/docs/Quickstart)
* [Documentation](https://automorph.org)
* [API](https://automorph.org/api/index.html)
* [Artifacts](https://central.sonatype.com/namespace/org.automorph)
* [Contact](mailto:automorph.org@proton.me)


# Build

## Requirements

* [JDK](https://openjdk.java.net/) 11+
* [SBT](https://www.scala-sbt.org/) 1.9+
* [NodeJS](https://nodejs.org/) 19+
* [Yarn](https://yarnpkg.com/) 1.22+

**Note**: uPickle plugin build may take a long time but it works.


## Testing

### Basic tests

```shell
sbt '+ test'
```

### Simple remote API tests only

```shell
TEST_LEVEL=simple sbt '+ test'
```

### Complex remote API tests including all integration tests

```shell
TEST_LEVEL=all sbt '+ test'
```

### Console log level

```shell
LOG_LEVEL=DEBUG sbt '+ test'
```

### Generated code logging

```shell
LOG_CODE=true sbt '+ test'
```

### Test logs

```
less target/test.log
```


## Documentation

### Generate website

```shell
sbt site
```

### Serve website

```shell
sbt serveSite
```

