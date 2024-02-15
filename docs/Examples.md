---
sidebar_position: 7
---

# Examples

The following examples illustrate how to address various remote call use-cases. 

They mostly use [default plugins](https://automorph.org/docs/Plugins) and focus on specific features
but will work with any other plugins or feature combinations.


## Basic

### [Asynchronous call](https://automorph.org/examples/src/main/scala/examples/basic/AsynchronousCall.scala)


### [Synchronous call](https://automorph.org/examples/src/main/scala/examples/basic/SynchronousCall.scala)


### [Optional parameters](https://automorph.org/examples/src/main/scala/examples/basic/OptionalParameters.scala)


### [Multiple APIs](https://automorph.org/examples/src/main/scala/examples/basic/MultipleApis.scala)


## Integration

### [Effect system](https://automorph.org/examples/src/main/scala/examples/integration/EffectSystem.scala)


### [Message codec](https://automorph.org/examples/src/main/scala/examples/integration/MessageCodec.scala)


### [RPC protocol](https://automorph.org/examples/src/main/scala/examples/integration/RpcProtocol.scala)


### [Custom server](https://automorph.org/examples/src/main/scala/examples/integration/CustomServer.scala)

**Test**

```scala
// Test the JSON-RPC request processing function
val requestBody =
  """
    |{
    |  "jsonrpc" : "2.0",
    |  "id" : "1234",
    |  "method" : "test",
    |  "params" : {
    |    "n" : 1
    |  }
    |}
    |""".getBytes(UTF_8)
val responseBody = processRpcRequest(requestBody)
responseBody.foreach { response =>
  println(new String(response, UTF_8))
}
```


## Transport

### [Client transport](https://automorph.org/examples/src/main/scala/examples/transport/ClientTransport.scala)


### [Server transport](https://automorph.org/examples/src/main/scala/examples/transport/ServerTransport.scala)


### [Endpoint transport](https://automorph.org/examples/src/main/scala/examples/transport/EndpointTransport.scala)


### [WebSocket transport](https://automorph.org/examples/src/main/scala/examples/transport/WebSocketTransport.scala)


### [AMQP transport](https://automorph.org/examples/src/main/scala/examples/transport/AmqpTransport.scala)


## Customization

### [Data type serialization](https://automorph.org/examples/src/main/scala/examples/customization/DataTypeSerialization.scala)


### [Client function names](https://automorph.org/examples/src/main/scala/examples/customization/ClientFunctionNames.scala)


### [Server function names](https://automorph.org/examples/src/main/scala/examples/customization/ServerFunctionNames.scala)


## Metadata

### [HTTP authentication](https://automorph.org/examples/src/main/scala/examples/metadata/HttpAuthentication.scala)


### [HTTP request properties](https://automorph.org/examples/src/main/scala/examples/metadata/HttpRequestProperties.scala)


### [HTTP response properties](https://automorph.org/examples/src/main/scala/examples/metadata/HttpResponseProperties.scala)


## Error handling

### [Client error mapping](https://automorph.org/examples/src/main/scala/examples/errorhandling/ClientErrorMapping.scala)


### [Server error mapping](https://automorph.org/examples/src/main/scala/examples/errorhandling/ServerErrorMapping.scala)


### [HTTP status code](https://automorph.org/examples/src/main/scala/examples/errorhandling/HttpStatusCode.scala)


## Special

### [API discovery](https://automorph.org/examples/src/main/scala/examples/special/ApiDiscovery.scala)


### [Dynamic payload](https://automorph.org/examples/src/main/scala/examples/special/DynamicPayload.scala)


### [One-way message](https://automorph.org/examples/src/main/scala/examples/special/OneWayMessage.scala)


### [Positional arguments](https://automorph.org/examples/src/main/scala/examples/special/PositionalArguments.scala)


### [Local call](https://automorph.org/examples/src/main/scala/examples/special/LocalCall.scala)
