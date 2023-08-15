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

**Source**

```scala
import automorph.Default
import automorph.system.IdentitySystem
import java.net.URI

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

**Source**

```scala
import automorph.Default
import automorph.transport.http.HttpMethod
import java.net.URI
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}

// Define a remote API
trait Api {
  def hello(some: String, n: Int): Future[String]
}

// Create server implementation of the remote API
val api = new Api {
  def hello(some: String, n: Int): Future[String] =
    Future(s"Hello $some $n!")
}

Await.ready(for {
  // Initialize JSON-RPC HTTP & WebSocket server listening on port 9000 for POST or PUT requests to '/api'
  server <- Default.rpcServer(9000, "/api", Seq(HttpMethod.Post, HttpMethod.Put)).bind(api).init()

  // Initialize JSON-RPC HTTP client for sending POST requests to 'http://localhost:9000/api'
  client <- Default.rpcClient(new URI("http://localhost:9000/api")).init()
  remoteApi = client.bind[Api]

  // Call the remote API function statically
  result <- remoteApi.hello("world", 1)
  _ = println(result)

  // Call the remote API function dynamically
  result <- client.call[String]("hello")("some" -> "world", "n" -> 1)
  _ = println(result)

  // Close the RPC client
  _ <- client.close()

  // Close the RPC server
  _ <- server.close()
} yield (), Duration.Inf)
```

### [Optional parameters](https://github.com/automorph-org/automorph/tree/main/examples/project/src/main/scala/examples/basic/OptionalParameters.scala)

**Build**

```scala
libraryDependencies ++= Seq(
  "org.automorph" %% "automorph-default" % "@PROJECT_VERSION@"
)
```

**Source**

```scala
import automorph.Default
import java.net.URI
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}

// Define client view of a remote API
trait Api {
  def hello(some: String): Future[String]
}

// Create server implementation of the remote API
class ApiImpl {
  def hello(some: String, n: Option[Int]): Future[String] =
    Future(s"Hello $some ${n.getOrElse(0)}!")

  def hi(some: Option[String])(n: Int): Future[String] =
    Future(s"Hi ${some.getOrElse("all")} $n!")
}
val api = new ApiImpl

Await.ready(for {
  // Initialize JSON-RPC HTTP client for sending POST requests to 'http://localhost:9000/api'
  server <- Default.rpcServer(9000, "/api").bind(api).init()

  // Initialize JSON-RPC HTTP client for sending POST requests to 'http://localhost:9000/api'
  client <- Default.rpcClient(new URI("http://localhost:9000/api")).init()
  remoteApi = client.bind[Api]

  // Call the remote API function statically
  result <- remoteApi.hello("world")
  _ = println(result)

  // Call the remote API function dynamically
  result <- client.call[String]("hi")("n" -> 1)
  _ = println(result)

  // Close the RPC client
  _ <- client.close()

  // Close the RPC server
  _ <- server.close()
} yield (), Duration.Inf)
```


## Customization

### [Data serialization](https://github.com/automorph-org/automorph/tree/main/examples/project/src/main/scala/examples/customization/DataSerialization.scala)

**Build**

```scala
libraryDependencies ++= Seq(
  "org.automorph" %% "automorph-default" % "@PROJECT_VERSION@"
)
```

**Source**

```scala
import automorph.Default
import io.circe.{Decoder, Encoder}
import io.circe.generic.auto.*
import java.net.URI
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}

// Introduce custom data types
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

// Define a remote API
trait Api {
  def hello(some: String, record: Record): Future[Record]
}

// Create server implementation of the remote API
val api = new Api {
  def hello(some: String, record: Record): Future[Record] =
    Future(record.copy(value = s"Hello $some!"))
}

Await.ready(for {
  // Initialize JSON-RPC HTTP & WebSocket server listening on port 9000 for requests to '/api'
  server <- Default.rpcServer(9000, "/api").bind(api).init()

  // Initialize JSON-RPC HTTP client for sending POST requests to 'http://localhost:9000/api'
  client <- Default.rpcClient(new URI("http://localhost:9000/api")).init()
  remoteApi = client.bind[Api]

  // Call the remote API function
  result <- remoteApi.hello("world", Record("test", State.On))
  _ = println(result)

  // Close the RPC client
  _ <- client.close()

  // Close the RPC server
  _ <- server.close()
} yield (), Duration.Inf)
```

### [Client function names](https://github.com/automorph-org/automorph/tree/main/examples/project/src/main/scala/examples/customization/ClientFunctionNames.scala)

**Build**

```scala
libraryDependencies ++= Seq(
  "org.automorph" %% "automorph-default" % "@PROJECT_VERSION@"
)
```

**Source**

```scala
import automorph.Default
import automorph.system.IdentitySystem
import java.net.URI

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

**Source**

```scala
import automorph.Default
import automorph.system.IdentitySystem
import java.net.URI
import scala.util.Try

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

**Source**

```scala
import automorph.{Default, RpcClient}
import java.net.URI
import java.sql.SQLException
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}

// Define a remote API
trait Api {
  def hello(some: String, n: Int): Future[String]
}

// Create server implementation of the remote API
val api = new Api {
  def hello(some: String, n: Int): Future[String] =
    Future.failed(new IllegalArgumentException("SQL error"))
}

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

Await.ready(for {
  // Initialize JSON-RPC HTTP & WebSocket server listening on port 9000 for requests to '/api'
  server <- Default.rpcServer(9000, "/api").bind(api).init()

  
  // Initialize custom JSON-RPC HTTP client
  client <- RpcClient.transport(clientTransport).rpcProtocol(rpcProtocol).init()
  remoteApi = client.bind[Api]

  // Call the remote API function and fail with SQLException
  error <- remoteApi.hello("world", 1).failed
  _ = println(error)

  // Close the RPC client
  _ <- client.close()

  // Close the RPC server
  _ <- server.close()
} yield (), Duration.Inf)
```

### [Server errors](https://github.com/automorph-org/automorph/tree/main/examples/project/src/main/scala/examples/errors/ServerErrors.scala)

**Build**

```scala
libraryDependencies ++= Seq(
  "org.automorph" %% "automorph-default" % "@PROJECT_VERSION@"
)
```

**Source**

```scala
import automorph.protocol.jsonrpc.ErrorType.InvalidRequest
import automorph.protocol.jsonrpc.JsonRpcException
import automorph.{Default, RpcServer}
import java.net.URI
import java.sql.SQLException
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}

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

Await.ready(for {
  // Initialize custom JSON-RPC HTTP & WebSocket server
  server <- RpcServer.transport(serverTransport).rpcProtocol(rpcProtocol).bind(api).init()

  // Initialize JSON-RPC HTTP client for sending POST requests to 'http://localhost:9000/api'
  client <- Default.rpcClient(new URI("http://localhost:9000/api")).init()
  remoteApi = client.bind[Api]

  // Call the remote API function and fail with InvalidRequestException
  error <- remoteApi.hello("world", 1).failed
  _ = println(error)

  // Call the remote API function and fail with RuntimeException
  error <- remoteApi.hello("world", -1).failed
  _ = println(error)

  // Close the RPC client
  _ <- client.close()

  // Close the RPC server
  _ <- server.close()
} yield (), Duration.Inf)
```

### [HTTP status code](https://github.com/automorph-org/automorph/tree/main/examples/project/src/main/scala/examples/errors/HttpStatusCode.scala)

**Build**

```scala
libraryDependencies ++= Seq(
  "org.automorph" %% "automorph-default" % "@PROJECT_VERSION@"
)
```

**Source**

```scala
import automorph.Default
import automorph.transport.http.HttpContext
import java.net.URI
import java.sql.SQLException
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}

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

Await.ready(for {
  // Initialize custom JSON-RPC HTTP & WebSocket server listening on port 9000 for requests to '/api'
  server <- Default.rpcServer(9000, "/api", mapException = mapException).bind(api).init()

  // Initialize JSON-RPC HTTP client for sending POST requests to 'http://localhost:9000/api'
  client <- Default.rpcClient(new URI("http://localhost:9000/api")).init()
  remoteApi = client.bind[Api]

  // Call the remote API function and fail with InvalidRequestException
  error <- remoteApi.hello("world", 1).failed
  _ = println(error)

  // Close the RPC client
  _ <- client.close()

  // Close the RPC server
  _ <- server.close()
} yield (), Duration.Inf)
```


## Metadata

### [HTTP authentication](https://github.com/automorph-org/automorph/tree/main/examples/project/src/main/scala/examples/metadata/HttpAuthentication.scala)

**Build**

```scala
libraryDependencies ++= Seq(
  "org.automorph" %% "automorph-default" % "@PROJECT_VERSION@"
)
```

**Source**

```scala
import automorph.Default
import automorph.Default.{ClientContext, ServerContext}
import automorph.system.IdentitySystem
import java.net.URI
import scala.util.Try

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

**Source**

```scala
import automorph.Default
import automorph.Default.{ClientContext, ServerContext}
import automorph.system.IdentitySystem
import java.net.URI


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

**Source**

```scala
import automorph.Default.{ClientContext, ServerContext}
import automorph.system.IdentitySystem
import automorph.transport.http.HttpContext
import automorph.{Default, RpcResult}
import java.net.URI

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

**Source**

```scala
import automorph.Default
import automorph.protocol.JsonRpcProtocol
import automorph.schema.{OpenApi, OpenRpc}
import java.net.URI
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}

Await.ready(for {
  // Initialize JSON-RPC HTTP & WebSocket server with API discovery listening on port 9000 for POST requests to '/api'
  server <- Default.rpcServer(9000, "/api").discovery(true).bind(api).init()

  // Initialize JSON-RPC HTTP client for sending POST requests to 'http://localhost:9000/api'
  client <- Default.rpcClient(new URI("http://localhost:9000/api")).init()
  remoteApi = client.bind[Api]

  // Retrieve the remote API schema in OpenRPC format
  result <- client.call[OpenRpc](JsonRpcProtocol.openRpcFunction)()
  _ = println(result.methods.map(_.name))

  // Retrieve the remote API schema in OpenAPI format
  result <- client.call[OpenApi](JsonRpcProtocol.openApiFunction)()
  _ = println(result.paths.get.keys.toList)

  // Close the RPC client
  _ <- client.close()

  // Close the RPC server
  _ <- server.close()
} yield (), Duration.Inf)
```

### [Dynamic payload](https://github.com/automorph-org/automorph/tree/main/examples/project/src/main/scala/examples/special/DynamicPayload.scala)

**Build**

```scala
libraryDependencies ++= Seq(
  "org.automorph" %% "automorph-default" % "@PROJECT_VERSION@"
)
```

**Source**

```scala
import automorph.Default
import io.circe.Json
import java.net.URI
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}

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


// Define client view of a remote API
trait Api {
  def hello(some: String, n: Json): Future[Json]
}

// Create server implementation of the remote API
class ApiImpl {
  def hello(some: Json, n: Int): Future[Json] =
    if (some.isString) {
      val value = some.as[String].toTry.get
      Future(Json.fromString(s"Hello $value $n!"))
    } else {
      Future(Json.fromValues(Seq(some, Json.fromInt(n))))
    }
}
val api = new ApiImpl

Await.ready(for {
  // Initialize JSON-RPC HTTP client for sending POST requests to 'http://localhost:9000/api'
  server <- Default.rpcServer(9000, "/api").bind(api).init()

  // Initialize JSON-RPC HTTP client for sending POST requests to 'http://localhost:9000/api'
  client <- Default.rpcClient(new URI("http://localhost:9000/api")).init()
  remoteApi = client.bind[Api]

  // Call the remote API function statically
  result <- remoteApi.hello("world", Json.fromInt(1))
  _ = println(result)

  // Call the remote API function dynamically
  result <- client.call[Seq[Int]]("hello")("some" -> Json.fromInt(0), "n" -> 1)
  _ = println(result)

  // Close the RPC client
  _ <- client.close()

  // Close the RPC server
  _ <- server.close()
} yield (), Duration.Inf)
```

### [One-way message](https://github.com/automorph-org/automorph/tree/main/examples/project/src/main/scala/examples/special/OneWayMessage.scala)

**Build**

```scala
libraryDependencies ++= Seq(
  "org.automorph" %% "automorph-default" % "@PROJECT_VERSION@"
)
```

**Source**

```scala
import automorph.Default
import java.net.URI
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}

// Define a remote API
trait Api {
  def hello(some: String, n: Int): Future[String]
}

// Create server implementation of the remote API
val api = new Api {
  def hello(some: String, n: Int): Future[String] =
    Future(s"Hello $some $n!")
}

Await.ready(for {
  // Initialize JSON-RPC HTTP & WebSocket server listening on port 9000 for requests to '/api'
  server <- Default.rpcServer(9000, "/api").bind(api).init()

  // Initialize JSON-RPC HTTP client for sending POST requests to 'http://localhost:9000/api'
  client <- Default.rpcClient(new URI("http://localhost:9000/api")).init()
  remoteApi = client.bind[Api]

  // Call the remote API function dynamically without expecting a response
  _ <- client.tell("hello")("some" -> "world", "n" -> 1)

  // Close the RPC client
  _ <- client.close()

  // Close the RPC server
  _ <- server.close()
} yield (), Duration.Inf)
```

### [Positional arguments](https://github.com/automorph-org/automorph/tree/main/examples/project/src/main/scala/examples/special/PositionalArguments.scala)

**Build**

```scala
libraryDependencies ++= Seq(
  "org.automorph" %% "automorph-default" % "@PROJECT_VERSION@"
)
```

**Source**

```scala
import automorph.{Default, RpcClient}
import java.net.URI
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}

// Define a remote API
trait Api {
  def hello(some: String, n: Int): Future[String]
}

// Create server implementation of the remote API
val api = new Api {
  def hello(some: String, n: Int): Future[String] =
    Future(s"Hello $some $n!")
}

// Configure JSON-RPC to pass arguments by position instead of by name
val rpcProtocol = Default.rpcProtocol[Default.ClientContext].namedArguments(false)

// Create HTTP client transport sending POST requests to 'http://localhost:9000/api'
val clientTransport = Default.clientTransport(new URI("http://localhost:9000/api"))

Await.ready(for {
  // Initialize JSON-RPC HTTP & WebSocket server with API discovery listening on port 9000 for POST requests to '/api'
  server <- Default.rpcServer(9000, "/api").discovery(true).bind(api).init()

  // Initialize custom JSON-RPC HTTP client
  client <- RpcClient.transport(clientTransport).rpcProtocol(rpcProtocol).init()
  remoteApi = client.bind[Api]

  // Call the remote API function
  result <- remoteApi.hello("world", 1)
  _ = println(result)

  // Close the RPC client
  _ <- client.close()

  // Close the RPC server
  _ <- server.close()
} yield (), Duration.Inf)
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

**Source**

```scala
import automorph.Default
import automorph.system.ZioSystem
import java.net.URI
import zio.{Task, Unsafe, ZIO}

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

Unsafe.unsafe { implicit unsafe =>
  ZioSystem.defaultRuntime.unsafe.run(
    for {
      // Initialize JSON-RPC HTTP & WebSocket server listening on port 9000 for requests to '/api'
      server <- Default.rpcServerCustom(effectSystem, 9000, "/api").bind(api).init()

      // Initialize JSON-RPC HTTP client for sending POST requests to 'http://localhost:9000/api'
      client <- Default.rpcClientCustom(effectSystem, new URI("http://localhost:9000/api")).init()
      remoteApi = client.bind[Api]

      // Call the remote API function
      result <- remoteApi.hello("world", 1)
      _ = println(result)

      // Close the RPC client
      _ <- client.close()

      // Close the RPC server
      _ <- server.close()
    } yield ()
  )
}
```

### [Message codec](https://github.com/automorph-org/automorph/tree/main/examples/project/src/main/scala/examples/integration/MessageCodec.scala)

**Build**

```scala
libraryDependencies ++= Seq(
  "org.automorph" %% "automorph-default" % "@PROJECT_VERSION@",
  "org.automorph" %% "automorph-upickle" % "@PROJECT_VERSION@"
)
```

**Source**

```scala
import automorph.codec.messagepack.{UpickleMessagePackCodec, UpickleMessagePackCustom}
import automorph.{Default, RpcClient, RpcServer}
import java.net.URI
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}

// Introduce custom data types
case class Record(values: List[String])

// Create uPickle message codec for JSON format
val messageCodec = UpickleMessagePackCodec[UpickleMessagePackCustom]()

// Provide custom data type serialization and deserialization logic
import messageCodec.custom.*
implicit def recordRw: messageCodec.custom.ReadWriter[Record] = messageCodec.custom.macroRW

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

// Create a client RPC protocol plugin
val clientRpcProtocol = Default.rpcProtocol[UpickleMessagePackCodec.Node, messageCodec.type, Default.ClientContext](
  messageCodec
)

// Create HTTP client transport sending POST requests to 'http://localhost:9000/api'
val clientTransport = Default.clientTransport(new URI("http://localhost:9000/api"))

Await.ready(for {
  // Initialize custom JSON-RPC HTTP & WebSocket server
  server <- RpcServer.transport(serverTransport).rpcProtocol(serverRpcProtocol).bind(api).init()

  // Initialize custom JSON-RPC HTTP client
  client <- RpcClient.transport(clientTransport).rpcProtocol(clientRpcProtocol).init()
  remoteApi = client.bind[Api]

  // Call the remote API function
  result <- remoteApi.hello("world", 1)
  _ = println(result)

  // Close the RPC client
  _ <- client.close()

  // Close the RPC server
  _ <- server.close()
} yield (), Duration.Inf)
```

### [RPC protocol](https://github.com/automorph-org/automorph/tree/main/examples/project/src/main/scala/examples/integration/RpcProtocol.scala)

**Build**

```scala
libraryDependencies ++= Seq(
  "org.automorph" %% "automorph-default" % "@PROJECT_VERSION@"
)
```

**Source**

```scala
import automorph.protocol.WebRpcProtocol
import automorph.{Default, RpcClient, RpcServer}
import java.net.URI
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}

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

// Create a client Web-RPC protocol plugin with '/api' path prefix
val clientRpcProtocol = WebRpcProtocol[Default.Node, Default.Codec, Default.ClientContext](
  Default.messageCodec, "/api"
)

// Create HTTP & WebSocket client transport sending POST requests to 'http://localhost:9000/api'
val clientTransport = Default.clientTransport(new URI("http://localhost:9000/api"))

Await.ready(for {
  // Initialize custom JSON-RPC HTTP & WebSocket server
  server <- RpcServer.transport(serverTransport).rpcProtocol(serverRpcProtocol).bind(api).init()

  // Initialize custom JSON-RPC HTTP client
  client <- RpcClient.transport(clientTransport).rpcProtocol(clientRpcProtocol).init()
  remoteApi = client.bind[Api]

  // Call the remote API function
  result <- remoteApi.hello("world", 1)
  _ = println(result)

  // Close the RPC client
  _ <- client.close()

  // Close the RPC server
  _ <- server.close()
} yield (), Duration.Inf)
```


## Transport

### [Client transport](https://github.com/automorph-org/automorph/tree/main/examples/project/src/main/scala/examples/transport/ClientTransport.scala)

**Build**

```scala
libraryDependencies ++= Seq(
  "org.automorph" %% "automorph-default" % "@PROJECT_VERSION@"
)
```

**Source**

```scala
import automorph.{Default, RpcClient}
import automorph.system.FutureSystem
import automorph.transport.http.client.UrlClient
import java.net.URI
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}

// Define a remote API
trait Api {
  def hello(some: String, n: Int): Future[String]
}

// Create server implementation of the remote API
val api = new Api {
  override def hello(some: String, n: Int): Future[String] =
    Future(s"Hello $some $n!")
}

// Create standard JRE HTTP client message transport sending POST requests to 'http://localhost:9000/api'
val clientTransport = UrlClient(FutureSystem(), new URI("http://localhost:9000/api"))

Await.ready(for {
  // Initialize JSON-RPC HTTP & WebSocket server listening on port 80 for requests to '/api'
  server <- Default.rpcServer(9000, "/api").bind(api).init()

  // Initialize custom JSON-RPC HTTP client
  client <- RpcClient.transport(clientTransport).rpcProtocol(Default.rpcProtocol).init()
  remoteApi = client.bind[Api]

  // Call the remote API function
  result <- remoteApi.hello("world", 1)
  _ = println(result)

  // Close the RPC client
  _ <- client.close()

  // Close the RPC server
  _ <- server.close()
} yield (), Duration.Inf)
```

### [Server transport](https://github.com/automorph-org/automorph/tree/main/examples/project/src/main/scala/examples/transport/ServerTransport.scala)

**Build**

```scala
libraryDependencies ++= Seq(
  "org.automorph" %% "automorph-default" % "@PROJECT_VERSION@"
)
```

**Source**

```scala
import automorph.{Default, RpcServer}
import automorph.system.FutureSystem
import automorph.transport.http.server.NanoServer
import java.net.URI
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}

// Define a remote API
trait Api {
  def hello(some: String, n: Int): Future[String]
}

// Create server implementation of the remote API
val api = new Api {
  override def hello(some: String, n: Int): Future[String] =
    Future(s"Hello $some $n!")
}

// Create NanoHTTPD HTTP & WebSocket server transport listening on port 9000 for requests to '/api'
val serverTransport = NanoServer(FutureSystem(), 9000, "/api")

Await.ready(for {
  // Initialize custom JSON-RPC HTTP & WebSocket server
  server <- RpcServer.transport(serverTransport).rpcProtocol(Default.rpcProtocol).bind(api).init()

  // Initialize JSON-RPC HTTP client for sending POST requests to 'http://localhost:9000/api'
  client <- Default.rpcClient(new URI("http://localhost:9000/api")).init()
  remoteApi = client.bind[Api]

  // Call the remote API function
  result <- remoteApi.hello("world", 1)
  _ = println(result)

  // Close the RPC client
  _ <- client.close()

  // Close the RPC server
  _ <- server.close()
} yield (), Duration.Inf)
```

### [Endpoint transport](https://github.com/automorph-org/automorph/tree/main/examples/project/src/main/scala/examples/transport/EndpointTransport.scala)

**Build**

```scala
libraryDependencies ++= Seq(
  "org.automorph" %% "automorph-default" % "@PROJECT_VERSION@"
)
```

**Source**

```scala
import automorph.{Default, Endpoint}
import automorph.transport.http.endpoint.UndertowHttpEndpoint
import io.undertow.{Handlers, Undertow}
import java.net.URI
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}

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

Await.ready(for {
  // Initialize JSON-RPC HTTP client for sending POST requests to 'http://localhost:9000/api'
  client <- Default.rpcClient(new URI("http://localhost:9000/api")).init()
  remoteApi = client.bind[Api]

  // Call the remote API function
  result <- remoteApi.hello("world", 1)
  _ = println(result)

  // Close the RPC client
  _ <- client.close()
} yield (), Duration.Inf)

// Close the RPC server
server.stop()
}

// Stop the Undertow server
server.stop()
```

### [WebSocket transport](https://github.com/automorph-org/automorph/tree/main/examples/project/src/main/scala/examples/transport/WebSocketTransport.scala)

**Build**

```scala
libraryDependencies ++= Seq(
  "org.automorph" %% "automorph-default" % "@PROJECT_VERSION@"
)
```

**Source**

```scala
import automorph.Default
import java.net.URI
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}

// Define a remote API
trait Api {
  def hello(some: String, n: Int): Future[String]
}

// Create server implementation of the remote API
val api = new Api {
  def hello(some: String, n: Int): Future[String] =
    Future(s"Hello $some $n!")
}

Await.ready(for {
  // Initialize JSON-RPC HTTP & WebSocket server listening on port 9000 for requests to '/api'
  server <- Default.rpcServer(9000, "/api").bind(api).init()

  // Initialize JSON-RPC WebSocket client for sending requests to 'ws://localhost:9000/api'
  client <- Default.rpcClient(new URI("ws://localhost:9000/api")).init()
  remoteApi = client.bind[Api]

  // Call the remote API function
  result <- remoteApi.hello("world", 1)
  _ = println(result)

  // Close the RPC client
  _ <- client.close()

  // Close the RPC server
  _ <- server.close()
} yield (), Duration.Inf)
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

**Source**

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

// Initialize RabbitMQ AMQP JSON-RPC server
val server = run(
  RpcServer.transport(serverTransport).rpcProtocol(Default.rpcProtocol).bind(api).init()
)

// Create RabbitMQ AMQP client message transport publishing requests to the 'api' queue
val clientTransport = RabbitMqClient(new URI("amqp://localhost:9000"), "api", Default.effectSystem)

Await.ready(for {
  // Initialize custom JSON-RPC AMQP server
  server <- RpcServer.transport(serverTransport).rpcProtocol(Default.rpcProtocol).bind(api).init()

  // Initialize custom JSON-RPC AMQP client
  client <- RpcClient.transport(clientTransport).rpcProtocol(Default.rpcProtocol).init()
  remoteApi = client.bind[Api]

  // Call the remote API function
  result <- remoteApi.hello("world", 1)
  _ = println(result)

  // Close the RPC client
  _ <- client.close()

  // Close the RPC server
  _ <- server.close()
} yield (), Duration.Inf)

// Stop embedded RabbitMQ broker
broker.stop()
val brokerDirectory = brokerConfig.getExtractionFolder.toPath.resolve(brokerConfig.getVersion.getExtractionFolder)
Files.walk(brokerDirectory).iterator().asScala.toSeq.reverse.foreach(_.toFile.delete())
```
