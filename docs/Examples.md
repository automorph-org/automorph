---
sidebar_position: 7
---

# Examples

Most of the following examples are using [default plugins](Plugins).


## Basic

### [Synchronous call](https://github.com/automorph-org/automorph/tree/main/examples/project/src/main/scala/examples/basic/SynchronousCall.scala)

**Build**

```scala
libraryDependencies ++= Seq(
  "org.automorph" %% "automorph-default" % "@PROJECT_VERSION@"
)
```

**Imports**

```scala
import automorph.Default
import automorph.system.IdentitySystem
import java.net.URI
```

**Server**

```scala
// Define a remote API
trait Api {
  def hello(some: String, n: Int): String
}

// Create server implementation of the remote API
val api = new Api {
  def hello(some: String, n: Int): String =
    s"Hello $some $n!"
}

// Initialize JSON-RPC HTTP & WebSocket server listening on port 9000 for POST requests to '/api'
val server = Default.rpcServerCustom(IdentitySystem(), 9000, "/api").bind(api).init()
```

**Client**

```scala
// Initialize JSON-RPC HTTP client sending POST requests to 'http://localhost:9000/api'
val client = Default.rpcClientCustom(IdentitySystem(), new URI("http://localhost:9000/api")).init()

// Call the remote API function statically
val remoteApi = client.bind[Api]
println(
  remoteApi.hello("world", 1)
)

// Call the remote API function dynamically
println(
  client.call[String]("hello")("some" -> "world", "n" -> 1)
)
```

**Cleanup**

```scala
// Close the RPC client
client.close()

// Close the RPC server
server.close()
```

### [Asynchronous call](https://github.com/automorph-org/automorph/tree/main/examples/project/src/main/scala/examples/basic/AsynchronousCall.scala)

**Build**

```scala
libraryDependencies ++= Seq(
  "org.automorph" %% "automorph-default" % "@PROJECT_VERSION@"
)
```

**Imports & Helpers**

```scala
import automorph.Default
import automorph.transport.http.HttpMethod
import java.net.URI
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}

// Helper function to evaluate Futures
def run[T](effect: Future[T]): T = Await.result(effect, Duration.Inf)
```

**Server**

```scala
// Define a remote API
trait Api {
  def hello(some: String, n: Int): Future[String]
}

// Create server implementation of the remote API
val api = new Api {
  def hello(some: String, n: Int): Future[String] =
    Future(s"Hello $some $n!")
}

// Initialize JSON-RPC HTTP & WebSocket server listening on port 9000 for POST or PUT requests to '/api'
val server = run(
  Default.rpcServer(9000, "/api", Seq(HttpMethod.Post, HttpMethod.Put)).bind(api).init()
)
```

**Client**

```scala
// Initialize JSON-RPC HTTP client sending PUT requests to 'http://localhost:9000/api'
val client = run(
  Default.rpcClient(new URI("http://localhost:9000/api"), HttpMethod.Put).init()
)

// Call the remote API function statically
val remoteApi = client.bind[Api]
println(run(
  remoteApi.hello("world", 1)
))

// Call the remote API function dynamically
println(run(
  client.call[String]("hello")("some" -> "world", "n" -> 1)
))
```

**Cleanup**

```scala
// Close the RPC client
run(client.close())

// Close the RPC server
run(server.close())
```

### [Optional parameters](https://github.com/automorph-org/automorph/tree/main/examples/project/src/main/scala/examples/basic/OptionalParameters.scala)

**Build**

```scala
libraryDependencies ++= Seq(
  "org.automorph" %% "automorph-default" % "@PROJECT_VERSION@"
)
```

**Imports**

```scala
import automorph.Default
import automorph.system.IdentitySystem
import java.net.URI
```

**Server**

```scala
// Define client view of a remote API
trait Api {
  def hello(some: String): String
}

// Create server implementation of the remote API
class ApiImpl {
  def hello(some: String, n: Option[Int]): String =
    s"Hello $some ${n.getOrElse(0)}!"

  def hi(some: Option[String])(n: Int): String =
    s"Hi ${some.getOrElse("all")} $n!"
}
val api = new ApiImpl

// Initialize JSON-RPC HTTP & WebSocket server listening on port 9000 for POST requests to '/api'
val server = Default.rpcServerCustom(IdentitySystem(), 9000, "/api").bind(api).init()
```

**Client**

```scala
// Initialize JSON-RPC HTTP client sending POST requests to 'http://localhost:9000/api'
val client = Default.rpcClientCustom(IdentitySystem(), new URI("http://localhost:9000/api")).init()

// Call the remote API function statically
val remoteApi = client.bind[Api]
println(
  remoteApi.hello("world")
)

// Call the remote API function dynamically
println(
  client.call[String]("hi")("n" -> 1)
)
```

**Cleanup**

```scala
// Close the RPC client
client.close()

// Close the RPC server
server.close()
```


## Customization

### [Data serialization](https://github.com/automorph-org/automorph/tree/main/examples/project/src/main/scala/examples/customization/DataSerialization.scala)

**Build**

```scala
libraryDependencies ++= Seq(
  "org.automorph" %% "automorph-default" % "@PROJECT_VERSION@"
)
```

**Imports & Helpers**

```scala
import automorph.Default
import io.circe.{Decoder, Encoder}
import io.circe.generic.auto.*
import java.net.URI
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}

// Helper function to evaluate Futures
def run[T](effect: Future[T]): T = Await.result(effect, Duration.Inf)
```

**Custom data types**
```scala
sealed abstract class State

object State {
  case object On extends State
  case object Off extends State
}

case class Record(
  value: String,
  state: State
)

// Data type serialization and deserialization logic
implicit val enumEncoder: Encoder[State] = Encoder.encodeInt.contramap[State](Map(
  State.Off -> 0,
  State.On -> 1
))
implicit val enumDecoder: Decoder[State] = Decoder.decodeInt.map(Map(
  0 -> State.Off,
  1 -> State.On
))
```

**Server**

```scala
// Define a remote API
trait Api {
  def hello(some: String, record: Record): Future[Record]
}

// Create server implementation of the remote API
val api = new Api {
  def hello(some: String, record: Record): Future[Record] =
    Future(record.copy(value = s"Hello $some!"))
}

// Initialize JSON-RPC HTTP & WebSocket server listening on port 9000 for requests to '/api'
val server = run(
  Default.rpcServer(9000, "/api").bind(api).init()
)
```

**Client**

```scala
// Initialize JSON-RPC HTTP client sending POST requests to 'http://localhost:9000/api'
val client = run(
  Default.rpcClient(new URI("http://localhost:9000/api")).init()
)

// Call the remote API function
val remoteApi = client.bind[Api]
println(run(
  remoteApi.hello("world", Record("test", State.On))
))
```

**Cleanup**

```scala
// Close the RPC client
run(client.close())

// Close the RPC server
run(server.close())
```

### [Client function names](https://github.com/automorph-org/automorph/tree/main/examples/project/src/main/scala/examples/customization/ClientFunctionNames.scala)

**Build**

```scala
libraryDependencies ++= Seq(
  "org.automorph" %% "automorph-default" % "@PROJECT_VERSION@"
)
```

**Imports**

```scala
import automorph.Default
import automorph.system.IdentitySystem
import java.net.URI
```

**Server**

```scala
// Define client view of a remote API
trait Api {
  def hello(some: String, n: Int): String

  // Invoked as 'hello'
  def hi(some: String, n: Int): String
}

// Create server implementation of the remote API
class ApiImpl {
  // Exposed both as 'hello' and 'hi'
  def hello(some: String, n: Int): String =
    s"Hello $some $n!"
}
val api = new ApiImpl

// Initialize JSON-RPC HTTP & WebSocket server listening on port 9000 for requests to '/api'
val server = Default.rpcServerCustom(IdentitySystem(), 9000, "/api").bind(api).init()
```

**Client**

```scala
// Initialize JSON-RPC HTTP client sending POST requests to 'http://localhost:9000/api'
val client = Default.rpcClientCustom(IdentitySystem(), new URI("http://localhost:9000/api")).init()

// Customize invoked API to RPC function name mapping
val mapName = (name: String) => name match {
  case "hi" => "hello"
  case other => other
}

// Call the remote API function
val remoteApi = client.bind[Api](mapName)
println(
  remoteApi.hello("world", 1)
)
println(
  remoteApi.hi("world", 1)
)
```

**Cleanup**

```scala
// Close the RPC client
client.close()

// Close the RPC server
server.close()
```

### [Server function names](https://github.com/automorph-org/automorph/tree/main/examples/project/src/main/scala/examples/customization/ServerFunctionNames.scala)

**Build**

```scala
libraryDependencies ++= Seq(
  "org.automorph" %% "automorph-default" % "@PROJECT_VERSION@"
)
```

**Imports**

```scala
import automorph.Default
import automorph.system.IdentitySystem
import java.net.URI
import scala.util.Try
```

**Server**

```scala
// Define client view of a remote API
trait Api {
  def hello(some: String, n: Int): String

  def hi(some: String, n: Int): String
}

// Create server implementation of the remote API
class ApiImpl {
  // Exposed both as 'hello' and 'hi'
  def hello(some: String, n: Int): String =
    s"Hello $some $n!"

  // Exposed as 'test.sum'
  def sum(numbers: List[Double]): Double =
    numbers.sum

  // Not exposed
  def hidden(): String =
    ""
}
val api = new ApiImpl

// Customize exposed API to RPC function name mapping
val mapName = (name: String) => name match {
  case "hello" => Seq("hello", "hi")
  case "hidden" => Seq.empty
  case other => Seq(s"test.$other")
}

// Initialize JSON-RPC HTTP & WebSocket server listening on port 9000 for requests to '/api'
val server = Default.rpcServerCustom(IdentitySystem(), 9000, "/api").bind(api, mapName).init()
```

**Client**

```scala
// Initialize JSON-RPC HTTP client sending POST requests to 'http://localhost:9000/api'
val client = Default.rpcClientCustom(IdentitySystem(), new URI("http://localhost:9000/api")).init()

// Call the remote API function statically
val remoteApi = client.bind[Api]
println(
  remoteApi.hello("world", 1)
)
println(
  remoteApi.hi("world", 1)
)

// Call the remote API function dynamically
println(
  client.call[Double]("test.sum")("numbers" -> List(1, 2, 3))
)

// Call the remote API function dynamically and fail with FunctionNotFoundException
println(Try(
  client.call[String]("hidden")()
).failed.get)
```

**Cleanup**

```scala
// Close the RPC client
client.close()

// Close the RPC server
server.close()
```


## Errors

### [Client exceptions](https://github.com/automorph-org/automorph/tree/main/examples/project/src/main/scala/examples/errors/ClientExceptions.scala)

**Build**

```scala
libraryDependencies ++= Seq(
  "org.automorph" %% "automorph-default" % "@PROJECT_VERSION@"
)
```

**Imports & Helpers**

```scala
import automorph.{Default, RpcClient}
import java.net.URI
import java.sql.SQLException
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}
import scala.util.Try

// Helper function to evaluate Futures
def run[T](effect: Future[T]): T = Await.result(effect, Duration.Inf)
```

**Server**

```scala
// Define a remote API
trait Api {
  def hello(some: String, n: Int): Future[String]
}

// Create server implementation of the remote API
val api = new Api {
  def hello(some: String, n: Int): Future[String] =
    Future.failed(new IllegalArgumentException("SQL error"))
}

// Initialize JSON-RPC HTTP & WebSocket server listening on port 9000 for requests to '/api'
val server = run(
  Default.rpcServer(9000, "/api").bind(api).init()
)
```

**Client**

```scala
// Customize remote API client RPC error to exception mapping
val rpcProtocol = Default.rpcProtocol[Default.ClientContext].mapError((message, code) =>
  if (message.contains("SQL")) {
    new SQLException(message)
  } else {
    Default.rpcProtocol.mapError(message, code)
  }
)

// Create HTTP client transport sending POST requests to 'http://localhost:9000/api'
val clientTransport = Default.clientTransport(new URI("http://localhost:9000/api"))

// Setup custom JSON-RPC HTTP client
val client = run(
  RpcClient.transport(clientTransport).rpcProtocol(rpcProtocol).init()
)

// Call the remote API function and fail with SQLException
val remoteApi = client.bind[Api]
println(Try(run(
  remoteApi.hello("world", 1)
)).failed.get)
```

**Cleanup**

```scala
// Close the RPC client
run(client.close())

// Close the RPC server
run(server.close())
```

### [Server errors](https://github.com/automorph-org/automorph/tree/main/examples/project/src/main/scala/examples/errors/ServerErrors.scala)

**Build**

```scala
libraryDependencies ++= Seq(
  "org.automorph" %% "automorph-default" % "@PROJECT_VERSION@"
)
```

**Imports & Helpers**

```scala
import automorph.protocol.jsonrpc.ErrorType.InvalidRequest
import automorph.protocol.jsonrpc.JsonRpcException
import automorph.{Default, RpcServer}
import java.net.URI
import java.sql.SQLException
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}
import scala.util.Try

// Helper function to evaluate Futures
def run[T](effect: Future[T]): T = Await.result(effect, Duration.Inf)
```

**Server**

```scala
// Define a remote API
trait Api {
  def hello(some: String, n: Int): Future[String]
}

// Create server implementation of the remote API
val api = new Api {
  def hello(some: String, n: Int): Future[String] =
    if (n >= 0) {
      Future.failed(new SQLException("Invalid request"))
    } else {
      Future.failed(JsonRpcException("Application error", 1))
    }
}

// Customize remote API server exception to RPC error mapping
val rpcProtocol = Default.rpcProtocol[Default.ServerContext].mapException(_ match {
  case _: SQLException => InvalidRequest
  case error => Default.rpcProtocol.mapException(error)
})

// Create HTTP & WebSocket server transport listening on port 9000 for requests to '/api'
val serverTransport = Default.serverTransport(9000, "/api")

// Initialize JSON-RPC HTTP & WebSocket server
val server = run(
  RpcServer.transport(serverTransport).rpcProtocol(rpcProtocol).bind(api).init()
)
```

**Client**

```scala
// Initialize JSON-RPC HTTP client sending POST requests to 'http://localhost:9000/api'
val client = run(
  Default.rpcClient(new URI("http://localhost:9000/api")).init()
)

// Call the remote API function and fail with InvalidRequestException
val remoteApi = client.bind[Api]
println(Try(run(
  remoteApi.hello("world", 1)
)).failed.get)

// Call the remote API function and fail with RuntimeException
println(Try(run(
  remoteApi.hello("world", -1)
)).failed.get)
```

**Cleanup**

```scala
// Close the RPC client
run(client.close())

// Close the RPC server
run(server.close())
```

### [HTTP status code](https://github.com/automorph-org/automorph/tree/main/examples/project/src/main/scala/examples/errors/HttpStatusCode.scala)

**Build**

```scala
libraryDependencies ++= Seq(
  "org.automorph" %% "automorph-default" % "@PROJECT_VERSION@"
)
```

**Imports & Helpers**

```scala
import automorph.Default
import automorph.transport.http.HttpContext
import java.net.URI
import java.sql.SQLException
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}
import scala.util.Try

// Helper function to evaluate Futures
def run[T](effect: Future[T]): T = Await.result(effect, Duration.Inf)
```

**Server**

```scala
// Define a remote API
trait Api {
  def hello(some: String, n: Int): Future[String]
}

// Create server implementation of the remote API
val api = new Api {
  def hello(some: String, n: Int): Future[String] =
    Future.failed(new SQLException("Invalid request"))
}

// Customize remote API server exception to HTTP status code mapping
val mapException = (error: Throwable) => error match {
  case _: SQLException => 400
  case e => HttpContext.defaultExceptionToStatusCode(e)
}

// Start custom JSON-RPC HTTP & WebSocket server listening on port 9000 for requests to '/api'
val server = run(
  Default.rpcServer(9000, "/api", mapException = mapException).bind(api).init()
)
```

**Client**

```scala
// Initialize JSON-RPC HTTP client sending POST requests to 'http://localhost:9000/api'
val client = run(
  Default.rpcClient(new URI("http://localhost:9000/api")).init()
)

// Call the remote API function and fail with InvalidRequestException
val remoteApi = client.bind[Api]
println(Try(run(
  remoteApi.hello("world", 1)
)).failed.get)
```

**Cleanup**

```scala
// Close the RPC client
run(client.close())

// Close the RPC server
run(server.close())
```


## Metadata

### [HTTP authentication](https://github.com/automorph-org/automorph/tree/main/examples/project/src/main/scala/examples/metadata/HttpAuthentication.scala)

**Build**

```scala
libraryDependencies ++= Seq(
  "org.automorph" %% "automorph-default" % "@PROJECT_VERSION@"
)
```

**Imports**

```scala
import automorph.Default
import automorph.Default.{ClientContext, ServerContext}
import automorph.system.IdentitySystem
import java.net.URI
import scala.util.Try
```

**Server**

```scala
// Define client view of a remote API
trait Api {

  // Accept HTTP request context consumed by the client message transport plugin
  def hello(message: String)(implicit http: ClientContext): String
}

// Create server implementation of the remote API
class ApiImpl {

  // Accept HTTP request context provided by the server message transport plugin
  def hello(message: String)(implicit httpRequest: ServerContext): String =
    httpRequest.authorization("Bearer") match {
      case Some("valid") => s"Hello $message!"
      case _ => throw new IllegalAccessException("Authentication failed")
    }
}
val api = new ApiImpl

// Initialize JSON-RPC HTTP & WebSocket server listening on port 9000 for requests to '/api'
val server = Default.rpcServerCustom(IdentitySystem(), 9000, "/api").bind(api).init()
```

**Client**

```scala
// Initialize JSON-RPC HTTP client sending POST requests to 'http://localhost:9000/api'
val client = Default.rpcClientCustom(IdentitySystem(), new URI("http://localhost:9000/api")).init()
val remoteApi = client.bind[Api]

{
  // Create client request context containing invalid HTTP authentication
  implicit val validAuthentication: ClientContext = client.context
    .authorization("Bearer", "valid")

  // Call the remote API function statically using valid authentication
  println(
    remoteApi.hello("test")
  )

  // Call the remote API function dynamically using valid authentication
  println(
    client.call[String]("hello")("message" -> "test")
  )
}

{
  // Create client request context containing invalid HTTP authentication
  implicit val invalidAuthentication: ClientContext = client.context
    .headers("X-Authentication" -> "unsupported")

  // Call the remote API function statically using invalid authentication
  println(Try(
    remoteApi.hello("test")
  ).failed.get)

  // Call the remote API function dynamically using invalid authentication
  println(Try(
    client.call[String]("hello")("message" -> "test")
  ).failed.get)
}
```

**Cleanup**

```scala
// Close the RPC client
client.close()

// Close the RPC server
server.close()
```

### [HTTP request](https://github.com/automorph-org/automorph/tree/main/examples/project/src/main/scala/examples/metadata/HttpRequest.scala)

**Build**

```scala
libraryDependencies ++= Seq(
  "org.automorph" %% "automorph-default" % "@PROJECT_VERSION@"
)
```

**Imports**

```scala
import automorph.Default
import automorph.Default.{ClientContext, ServerContext}
import automorph.system.IdentitySystem
import java.net.URI

```

**Server**

```scala
// Define client view of a remote API
trait Api {

  // Accept HTTP request context consumed by the client message transport plugin
  def hello(message: String)(implicit http: ClientContext): String
}

// Create server implementation of the remote API
class ApiImpl {
  // Accept HTTP request context provided by the server message transport plugin
  def hello(message: String)(implicit httpRequest: ServerContext): String =
    Seq(
      Some(message),
      httpRequest.path,
      httpRequest.header("X-Test")
    ).flatten.mkString(",")
}
val api = new ApiImpl

// Initialize JSON-RPC HTTP & WebSocket server listening on port 9000 for requests to '/api'
val server = Default.rpcServerCustom(IdentitySystem(), 9000, "/api").bind(api).init()
```

**Client**

```scala
// Initialize JSON-RPC HTTP client sending POST requests to 'http://localhost:9000/api'
val client = Default.rpcClientCustom(IdentitySystem(), new URI("http://localhost:9000/api")).init()

// Create client request context specifying HTTP request metadata
implicit val httpRequest: ClientContext = client.context
  .parameters("test" -> "value")
  .headers("X-Test" -> "value", "Cache-Control" -> "no-cache")
  .cookies("Test" -> "value")
  .authorization("Bearer", "value")

// Call the remote API function statically using implicitly given HTTP request metadata
val remoteApi = client.bind[Api]
println(
  remoteApi.hello("test")
)

// Call the remote API function dynamically using implicitly given HTTP request metadata
println(
  client.call[String]("hello")("message" -> "test")
)
```

**Cleanup**

```scala
// Close the RPC client
client.close()

// Close the RPC server
server.close()
```

### [HTTP response](https://github.com/automorph-org/automorph/tree/main/examples/project/src/main/scala/examples/metadata/HttpResponse.scala)

**Build**

```scala
libraryDependencies ++= Seq(
  "org.automorph" %% "automorph-default" % "@PROJECT_VERSION@"
)
```

**Imports**

```scala
import automorph.Default.{ClientContext, ServerContext}
import automorph.system.IdentitySystem
import automorph.transport.http.HttpContext
import automorph.{Default, RpcResult}
import java.net.URI
```

**Server**

```scala
// Define client view of a remote API
trait Api {

  // Return HTTP response context provided by the client message transport plugin
  def hello(message: String): RpcResult[String, ClientContext]
}

// Create server implementation of the remote API
class ApiImpl {

  // Return HTTP response context consumed by the server message transport plugin
  def hello(message: String): RpcResult[String, ServerContext] = RpcResult(
    message,
    HttpContext().headers("X-Test" -> "value", "Cache-Control" -> "no-cache").statusCode(200)
  )
}
val api = new ApiImpl

// Initialize JSON-RPC HTTP & WebSocket server listening on port 9000 for requests to '/api'
val server = Default.rpcServerCustom(IdentitySystem(), 9000, "/api").bind(api).init()
```

**Client**

```scala
// Initialize JSON-RPC HTTP client sending POST requests to 'http://localhost:9000/api'
val client = Default.rpcClientCustom(IdentitySystem(), new URI("http://localhost:9000/api")).init()

// Call the remote API function statically retrieving a result with HTTP response metadata
val remoteApi = client.bind[Api]
val static = remoteApi.hello("test")
println(static.result)
println(static.context.header("X-Test"))

// Call the remote API function dynamically retrieving a result with HTTP response metadata
val dynamic = client.call[RpcResult[String, ClientContext]]("hello")("message" -> "test")
println(dynamic.result)
println(dynamic.context.header("X-Test"))
```

**Cleanup**

```scala
// Close the RPC client
client.close()

// Close the RPC server
server.close()
```


## Special

### [API discovery](https://github.com/automorph-org/automorph/tree/main/examples/project/src/main/scala/examples/special/ApiSchema.scala)

**Build**

```scala
libraryDependencies ++= Seq(
  "org.automorph" %% "automorph-default" % "@PROJECT_VERSION@"
)
```

**Imports & Helpers**

```scala
import automorph.Default
import automorph.protocol.JsonRpcProtocol
import automorph.schema.{OpenApi, OpenRpc}
import java.net.URI
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}

// Helper function to evaluate Futures
def run[T](effect: Future[T]): T = Await.result(effect, Duration.Inf)
```

**Server**

```scala
// Define a remote API
trait Api {
  def hello(some: String, n: Int): Future[String]
}

// Create server implementation of the remote API
val api = new Api {
  def hello(some: String, n: Int): Future[String] =
    Future(s"Hello $some $n!")
}

// Initialize JSON-RPC HTTP & WebSocket server with API discovery listening on port 9000 for POST requests to '/api'
val server = run(
  Default.rpcServer(9000, "/api").discovery(true).bind(api).init()
)
```

**Client**

```scala
// Initialize JSON-RPC HTTP client sending POST requests to 'http://localhost:9000/api'
val client = run(
  Default.rpcClient(new URI("http://localhost:9000/api")).init()
)

// Retrieve the remote API schema in OpenRPC format
println(run(
  client.call[OpenRpc](JsonRpcProtocol.openRpcFunction)()
).methods.map(_.name))

// Retrieve the remote API schema in OpenAPI format
println(run(
  client.call[OpenApi](JsonRpcProtocol.openApiFunction)(),
).paths.get.keys.toList)
```

**Cleanup**

```scala
// Close the RPC client
run(client.close())

// Close the RPC server
run(server.close())
```

### [Dynamic payload](https://github.com/automorph-org/automorph/tree/main/examples/project/src/main/scala/examples/special/DynamicPayload.scala)

**Build**

```scala
libraryDependencies ++= Seq(
  "org.automorph" %% "automorph-default" % "@PROJECT_VERSION@"
)
```

**Imports**

```scala
import automorph.Default
import automorph.system.IdentitySystem
import io.circe.Json
import java.net.URI
```

**Server**

```scala
// Define client view of a remote API
trait Api {
  def hello(some: String, n: Json): Json
}

// Create server implementation of the remote API
class ApiImpl {
  def hello(some: Json, n: Int): Json =
    if (some.isString) {
      val value = some.as[String].toTry.get
      Json.fromString(s"Hello $value $n!")
    } else {
      Json.fromValues(Seq(some, Json.fromInt(n)))
    }
}
val api = new ApiImpl

// Initialize JSON-RPC HTTP & WebSocket server listening on port 9000 for PUT requests to '/api'
val server = Default.rpcServerCustom(IdentitySystem(), 9000, "/api").bind(api).init()
```

**Client**

```scala
// Initialize JSON-RPC HTTP client sending PUT requests to 'http://localhost:9000/api'
val client = Default.rpcClientCustom(IdentitySystem(), new URI("http://localhost:9000/api")).init()

// Call the remote API function statically
val remoteApi = client.bind[Api]
println(
  remoteApi.hello("world", Json.fromInt(1))
)

// Call the remote API function dynamically
println(
  client.call[Seq[Int]]("hello")("some" -> Json.fromInt(0), "n" -> 1)
)
```

**Cleanup**

```scala
// Close the RPC client
client.close()

// Close the RPC server
server.close()
```

### [One-way message](https://github.com/automorph-org/automorph/tree/main/examples/project/src/main/scala/examples/special/OneWayMessage.scala)

**Build**

```scala
libraryDependencies ++= Seq(
  "org.automorph" %% "automorph-default" % "@PROJECT_VERSION@"
)
```

**Imports & Helpers**

```scala
import automorph.Default
import java.net.URI
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}

// Helper function to evaluate Futures
def run[T](effect: Future[T]): T = Await.result(effect, Duration.Inf)
```

**Server**

```scala
// Define a remote API
trait Api {
  def hello(some: String, n: Int): Future[String]
}

// Create server implementation of the remote API
val api = new Api {
  def hello(some: String, n: Int): Future[String] =
    Future(s"Hello $some $n!")
}

// Initialize JSON-RPC HTTP & WebSocket server listening on port 9000 for requests to '/api'
val server = run(
  Default.rpcServer(9000, "/api").bind(api).init()
)
```

**Client**

```scala
// Initialize JSON-RPC HTTP client sending POST requests to 'http://localhost:9000/api'
val client = run(
  Default.rpcClient(new URI("http://localhost:9000/api")).init()
)

// Call the remote API function dynamically without expecting a response
run(
  client.tell("hello")("some" -> "world", "n" -> 1)
)
```

**Cleanup**

```scala
// Close the RPC client
run(client.close())

// Close the RPC server
run(server.close())
```

### [Positional arguments](https://github.com/automorph-org/automorph/tree/main/examples/project/src/main/scala/examples/special/PositionalArguments.scala)

**Build**

```scala
libraryDependencies ++= Seq(
  "org.automorph" %% "automorph-default" % "@PROJECT_VERSION@"
)
```

**Imports & Helpers**

```scala
import automorph.{Default, RpcClient}
import java.net.URI
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}

// Helper function to evaluate Futures
def run[T](effect: Future[T]): T = Await.result(effect, Duration.Inf)
```

**Server**

```scala
// Define a remote API
trait Api {
  def hello(some: String, n: Int): Future[String]
}

// Create server implementation of the remote API
val api = new Api {
  def hello(some: String, n: Int): Future[String] =
    Future(s"Hello $some $n!")
}

// Initialize JSON-RPC HTTP & WebSocket server listening on port 9000 for POST requests to '/api'
val server = run(
  Default.rpcServer(9000, "/api").bind(api).init(),
)
```

**Client**

```scala
// Configure JSON-RPC to pass arguments by position instead of by name
val rpcProtocol = Default.rpcProtocol[Default.ClientContext].namedArguments(false)

// Create HTTP client transport sending POST requests to 'http://localhost:9000/api'
val clientTransport = Default.clientTransport(new URI("http://localhost:9000/api"))

// Setup  JSON-RPC HTTP client
val client = run(
  RpcClient.transport(clientTransport).rpcProtocol(rpcProtocol).init()
)

// Call the remote API function
val remoteApi = client.bind[Api]
println(run(
  remoteApi.hello("world", 1),
))
```

**Cleanup**

```scala
// Close the RPC client
run(client.close())

// Close the RPC server
run(server.close())
```


## Integration

### [Effect system](https://github.com/automorph-org/automorph/tree/main/examples/project/src/main/scala/examples/integration/EffectSystem.scala)

**Build**

```scala
libraryDependencies ++= Seq(
  "org.automorph" %% "automorph-default" % "@PROJECT_VERSION@",
  "org.automorph" %% "automorph-zio" % "@PROJECT_VERSION@",
  "com.softwaremill.sttp.client3" %% "async-http-client-backend-zio" % "3.3.9"
)
```

**Imports & Helpers**

```scala
import automorph.Default
import automorph.system.ZioSystem
import java.net.URI
import zio.{Task, Unsafe, ZIO}

// Helper function to evaluate ZIO tasks
def run[T](effect: Task[T]): T = Unsafe.unsafe { implicit unsafe =>
  ZioSystem.defaultRuntime.unsafe.run(effect).toEither.swap.map(_.getCause).swap.toTry.get
}
```

**Server**
```scala
// Define a remote API
trait Api {
  def hello(some: String, n: Int): Task[String]
}

// Create server implementation of the remote API
val api = new Api {
  def hello(some: String, n: Int): Task[String] =
    ZIO.succeed(s"Hello $some $n!")
}

// Create ZIO effect system plugin
val effectSystem = ZioSystem.default

// Initialize JSON-RPC HTTP & WebSocket server listening on port 9000 for requests to '/api'
val server = run(
  Default.rpcServerCustom(effectSystem, 9000, "/api").bind(api).init()
)
```

**Client**

```scala
// Create ZIO effect system plugin
val effectSystem = ZioSystem.default

// Initialize JSON-RPC HTTP client sending POST requests to 'http://localhost:9000/api'
val client = run(
  Default.rpcClientCustom(effectSystem, new URI("http://localhost:9000/api")).init()
)

// Call the remote API function via proxy
val remoteApi = client.bind[Api]
println(run(
  remoteApi.hello("world", 1)
))
```

**Cleanup**

```scala
// Close the RPC client
run(client.close())

// Close the RPC server
run(server.close())
```

### [Message codec](https://github.com/automorph-org/automorph/tree/main/examples/project/src/main/scala/examples/integration/MessageCodec.scala)

**Build**

```scala
libraryDependencies ++= Seq(
  "org.automorph" %% "automorph-default" % "@PROJECT_VERSION@",
  "org.automorph" %% "automorph-upickle" % "@PROJECT_VERSION@"
)
```

**Imports & Helpers**

```scala
import automorph.codec.messagepack.{UpickleMessagePackCodec, UpickleMessagePackCustom}
import automorph.{Default, RpcClient, RpcServer}
import java.net.URI
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}

// Helper function to evaluate Futures
def run[T](effect: Future[T]): T = Await.result(effect, Duration.Inf)
```

**Data types**
```scala
// Introduce custom data types
case class Record(values: List[String])

// Create uPickle message codec for JSON format
val messageCodec = UpickleMessagePackCodec[UpickleMessagePackCustom]()

// Provide custom data type serialization and deserialization logic
import messageCodec.custom.*
implicit def recordRw: messageCodec.custom.ReadWriter[Record] = messageCodec.custom.macroRW
```

**Server**

```scala
// Define a remote API
trait Api {
  def hello(some: String, n: Int): Future[Record]
}

// Create server implementation of the remote API
val api = new Api {
  def hello(some: String, n: Int): Future[Record] =
    Future(Record(List("Hello", some, n.toString)))
}

// Create a server RPC protocol plugin
val serverRpcProtocol = Default.rpcProtocol[UpickleMessagePackCodec.Node, messageCodec.type, Default.ServerContext](
  messageCodec
)

// Create HTTP & WebSocket server transport listening on port 9000 for requests to '/api'
val serverTransport = Default.serverTransport(9000, "/api")

// Initialize JSON-RPC HTTP & WebSocket server
val server = run(
  RpcServer.transport(serverTransport).rpcProtocol(serverRpcProtocol).bind(api).init()
)
```

**Client**

```scala
// Create a client RPC protocol plugin
val clientRpcProtocol = Default.rpcProtocol[UpickleMessagePackCodec.Node, messageCodec.type, Default.ClientContext](
  messageCodec
)

// Create HTTP client transport sending POST requests to 'http://localhost:9000/api'
val clientTransport = Default.clientTransport(new URI("http://localhost:9000/api"))

// Setup JSON-RPC HTTP & WebSocket client
val client = run(
  RpcClient.transport(clientTransport).rpcProtocol(clientRpcProtocol).init()
)

// Call the remote API function
val remoteApi = client.bind[Api]
println(run(
  remoteApi.hello("world", 1)
))
```

**Cleanup**

```scala
// Close the RPC client
run(client.close())

// Close the RPC server
run(server.close())
```

### [RPC protocol](https://github.com/automorph-org/automorph/tree/main/examples/project/src/main/scala/examples/integration/RpcProtocol.scala)

**Build**

```scala
libraryDependencies ++= Seq(
  "org.automorph" %% "automorph-default" % "@PROJECT_VERSION@"
)
```

**Imports & Helpers**

```scala
import automorph.protocol.WebRpcProtocol
import automorph.{Default, RpcClient, RpcServer}
import java.net.URI
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}

// Helper function to evaluate Futures
def run[T](effect: Future[T]): T = Await.result(effect, Duration.Inf)
```

**Server**

```scala
// Define a remote API
trait Api {
  def hello(some: String, n: Int): Future[String]
}

// Create server implementation of the remote API
val api = new Api {
  def hello(some: String, n: Int): Future[String] =
    Future(s"Hello $some $n!")
}

// Create a server Web-RPC protocol plugin with '/api' path prefix
val serverRpcProtocol = WebRpcProtocol[Default.Node, Default.Codec, Default.ServerContext](
  Default.messageCodec, "/api"
)

// Create HTTP & WebSocket server transport listening on port 9000 for requests to '/api'
val serverTransport = Default.serverTransport(9000, "/api")

// Start Web-RPC HTTP & WebSocket server
val server = run(
  RpcServer.transport(serverTransport).rpcProtocol(serverRpcProtocol).bind(api).init()
)
```

**Client**

```scala
// Create a client Web-RPC protocol plugin with '/api' path prefix
val clientRpcProtocol = WebRpcProtocol[Default.Node, Default.Codec, Default.ClientContext](
  Default.messageCodec, "/api"
)

// Create HTTP & WebSocket client transport sending POST requests to 'http://localhost:9000/api'
val clientTransport = Default.clientTransport(new URI("http://localhost:9000/api"))

// Setup Web-RPC HTTP client
val client = run(
  RpcClient.transport(clientTransport).rpcProtocol(clientRpcProtocol).init()
)

// Call the remote API function
val remoteApi = client.bind[Api]
println(run(
  remoteApi.hello("world", 1)
))
```

**Cleanup**

```scala
// Close the RPC client
run(client.close())

// Close the RPC server
run(server.close())
```


## Transport

### [Client transport](https://github.com/automorph-org/automorph/tree/main/examples/project/src/main/scala/examples/transport/ClientTransport.scala)

**Build**

```scala
libraryDependencies ++= Seq(
  "org.automorph" %% "automorph-default" % "@PROJECT_VERSION@"
)
```

**Imports**

```scala
import automorph.{Default, RpcClient}
import automorph.system.IdentitySystem
import automorph.transport.http.client.UrlClient
import java.net.URI
```

**Server**

```scala
// Define a remote API
trait Api {
  def hello(some: String, n: Int): String
}

// Create server implementation of the remote API
val api = new Api {
  def hello(some: String, n: Int): String =
    s"Hello $some $n!"
}

// Initialize JSON-RPC HTTP & WebSocket server listening on port 80 for requests to '/api'
val server = Default.rpcServerCustom(IdentitySystem(), 9000, "/api").bind(api).init()
```

**Client**

```scala
// Create standard JRE HTTP & WebSocket client message transport sending POST requests to 'http://localhost:9000/api'
val clientTransport = UrlClient(IdentitySystem(), new URI("http://localhost:9000/api"))

// Setup JSON-RPC HTTP & WebSocket client
val client = RpcClient.transport(clientTransport).rpcProtocol(Default.rpcProtocol).init()

// Call the remote API function via proxy
val remoteApi = client.bind[Api]
println(
  remoteApi.hello("world", 1)
)
```

**Cleanup**

```scala
// Close the RPC client
client.close()

// Close the RPC server
server.close()
```

### [Server transport](https://github.com/automorph-org/automorph/tree/main/examples/project/src/main/scala/examples/transport/ServerTransport.scala)

**Build**

```scala
libraryDependencies ++= Seq(
  "org.automorph" %% "automorph-default" % "@PROJECT_VERSION@"
)
```

**Imports**

```scala
import automorph.{Default, RpcServer}
import automorph.system.IdentitySystem
import automorph.transport.http.server.NanoServer
import java.net.URI
```

**Server**

```scala
// Define a remote API
trait Api {
  def hello(some: String, n: Int): String
}

// Create server implementation of the remote API
val api = new Api {
  def hello(some: String, n: Int): String =
    s"Hello $some $n!"
}

// Create NanoHTTPD HTTP & WebSocket server transport listening on port 9000 for requests to '/api'
val serverTransport = NanoServer(IdentitySystem(), 9000, "/api")

// Initialize JSON-RPC HTTP & WebSocket server
val server = RpcServer.transport(serverTransport).rpcProtocol(Default.rpcProtocol).bind(api).init()
```

**Client**

```scala
// Initialize JSON-RPC HTTP client sending POST requests to 'http://localhost:9000/api'
val client = Default.rpcClientCustom(IdentitySystem(), new URI("http://localhost:9000/api"))

// Call the remote API function
val remoteApi = client.bind[Api]
println(
  remoteApi.hello("world", 1)
)
```

**Cleanup**

```scala
// Close the RPC client
client.close()

// Close the RPC server
server.close()
```

### [Endpoint transport](https://github.com/automorph-org/automorph/tree/main/examples/project/src/main/scala/examples/transport/EndpointTransport.scala)

**Build**

```scala
libraryDependencies ++= Seq(
  "org.automorph" %% "automorph-default" % "@PROJECT_VERSION@"
)
```

**Imports & Helpers**

```scala
import automorph.{Default, Endpoint}
import automorph.transport.http.endpoint.UndertowHttpEndpoint
import io.undertow.{Handlers, Undertow}
import java.net.URI
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}

// Helper function to evaluate Futures
def run[T](effect: Future[T]): T = Await.result(effect, Duration.Inf)
```

**Server**

```scala
// Define a remote API
trait Api {
  def hello(some: String, n: Int): Future[String]
}

// Create server implementation of the remote API
val api = new Api {
  def hello(some: String, n: Int): Future[String] =
    Future(s"Hello $some $n!")
}

// Create Undertow JSON-RPC endpoint transport
val endpointTransport = UndertowHttpEndpoint(Default.effectSystem)

// Setup JSON-RPC endpoint
val endpoint = Endpoint.transport(endpointTransport).rpcProtocol(Default.rpcProtocol).bind(api)

// Start Undertow HTTP & WebSocket server listening on port 9000 for requests to '/api'
val server = Undertow.builder()
  .addHttpListener(9000, "0.0.0.0")
  .setHandler(Handlers.path().addPrefixPath("/api", endpoint.adapter))
  .build()
server.start()
```

**Client**

```scala
// Initialize JSON-RPC HTTP client sending POST requests to 'http://localhost:9000/api'
val client = run(
  Default.rpcClient(new URI("http://localhost:9000/api")).init()
)

// Call the remote API function via proxy
val remoteApi = client.bind[Api]
println(run(
  remoteApi.hello("world", 1)
))
```

**Cleanup**

```scala
// Close the RPC client
run(client.close())

// Close the RPC server
server.stop()
```

### [WebSocket transport](https://github.com/automorph-org/automorph/tree/main/examples/project/src/main/scala/examples/transport/WebSocketTransport.scala)

**Build**

```scala
libraryDependencies ++= Seq(
  "org.automorph" %% "automorph-default" % "@PROJECT_VERSION@"
)
```

**Imports**

```scala
import automorph.Default
import java.net.URI
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}

// Helper function to evaluate Futures
def run[T](effect: Future[T]): T = Await.result(effect, Duration.Inf)
```

**Server**

```scala
// Define a remote API
trait Api {
  def hello(some: String, n: Int): Future[String]
}

// Create server implementation of the remote API
val api = new Api {
  def hello(some: String, n: Int): Future[String] =
    Future(s"Hello $some $n!")
}

// Initialize JSON-RPC HTTP & WebSocket server listening on port 9000 for requests to '/api'
val server = run(
  Default.rpcServer(9000, "/api").bind(api).init()
)
```

**Client**

```scala
// Initialize JSON-RPC WebSocket client sending requests to 'ws://localhost:9000/api'
val client = Default.rpcClient(new URI("ws://localhost:9000/api"))

// Call the remote API function via proxy
val remoteApi = client.bind[Api]
println(run(
  remoteApi.hello("world", 1),
))
```

**Cleanup**

```scala
// Close the RPC client
run(client.close())

// Close the RPC server
run(server.close())
```

### [AMQP transport](https://github.com/automorph-org/automorph/tree/main/examples/project/src/main/scala/examples/transport/AmqpTransport.scala)

**Build**

```scala
libraryDependencies ++= Seq(
  "org.automorph" %% "automorph-default" % "@PROJECT_VERSION@",
  "org.automorph" %% "automorph-rabbitmq" % "@PROJECT_VERSION@",
  "io.arivera.oss" % "embedded-rabbitmq" % "1.5.0"
)
```

**Imports & Helpers**

```scala
import automorph.{Default, RpcClient, RpcServer}
import automorph.transport.amqp.client.RabbitMqClient
import automorph.transport.amqp.server.RabbitMqServer
import io.arivera.oss.embedded.rabbitmq.{EmbeddedRabbitMq, EmbeddedRabbitMqConfig}
import java.net.URI
import java.nio.file.Files
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}
import scala.jdk.CollectionConverters.IteratorHasAsScala
import scala.sys.process.Process
import scala.util.Try

// Helper function to evaluate Futures
def run[T](effect: Future[T]): T = Await.result(effect, Duration.Inf)
```

**Server**

```scala
// Define a remote API
trait Api {
  def hello(some: String, n: Int): Future[String]
}

// Create server implementation of the remote API
val api = new Api {
  def hello(some: String, n: Int): Future[String] =
    Future(s"Hello $some $n!")
}

// Start embedded RabbitMQ broker
val brokerConfig = new EmbeddedRabbitMqConfig.Builder().port(9000)
  .rabbitMqServerInitializationTimeoutInMillis(30000).build()
val broker = new EmbeddedRabbitMq(brokerConfig)
broker.start()

// Create RabbitMQ AMQP server transport consuming requests from the 'api' queue
val serverTransport = RabbitMqServer(Default.effectSystem, new URI("amqp://localhost:9000"), Seq("api"))

// Start RabbitMQ AMQP JSON-RPC server
val server = run(
  RpcServer.transport(serverTransport).rpcProtocol(Default.rpcProtocol).bind(api).init()
)
```

**Client**

```scala
// Create RabbitMQ AMQP client message transport publishing requests to the 'api' queue
val clientTransport = RabbitMqClient(new URI("amqp://localhost:9000"), "api", Default.effectSystem)

// Setup JSON-RPC HTTP & WebSocket client
val client = run(
  RpcClient.transport(clientTransport).rpcProtocol(Default.rpcProtocol).init()
)

// Call the remote API function
val remoteApi = client.bind[Api]
println(run(
  remoteApi.hello("world", 1)
))
```

**Cleanup**

```scala
// Close the RPC client
run(client.close())

// Close the RPC server
run(server.close())

// Stop embedded RabbitMQ broker
broker.stop()
val brokerDirectory = brokerConfig.getExtractionFolder.toPath.resolve(brokerConfig.getVersion.getExtractionFolder)
Files.walk(brokerDirectory).iterator().asScala.toSeq.reverse.foreach(_.toFile.delete())
```
