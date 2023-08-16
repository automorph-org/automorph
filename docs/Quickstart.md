---
sidebar_position: 2
---

# Quickstart

Expose and call a remote JSON-RPC API over HTTP.


## Script

Download and run an example using [Scala CLI](https://scala-cli.virtuslab.org):

```shell
scala-cli https://raw.githubusercontent.com/automorph-org/automorph/main/examples/project/src/main/scala/examples/Quickstart.scala
```


## New project

Create an [SBT](https://www.scala-sbt.org/) project from a [Giter8](http://www.foundweekends.org/giter8/) template:

```shell
sbt new automorph-org/automorph.g8
cd automorph-example
sbt run
```

Customize the example by editing `src/main/scala/examples/Quickstart.scala` and `build.sbt`.

Application logs are saved to `target/main.log` using the `LOG_LEVEL` environment variable to set a log level (`ERROR`, `WARN`, `INFO`, `DEBUG`, `TRACE`).


## Example project

Clone the the [example project](@REPOSITORY_URL@/tree/main/examples/project) and run any of the examples:

```shell
git clone --depth 1 @REPOSITORY_URL@
cd automorph/examples/project
sbt run
```

Customize the [examples](@REPOSITORY_URL@/blob/main/examples/project/src/main/scala/examples) by:
- Integrating with preferred platforms by including additional plugins
- Configuring RPC client, server or endpoint properties
- Removing unused examples and build dependencies


## Existing project

### Build

Add the following dependency to your build configuration:

#### SBT

```scala
libraryDependencies ++= Seq(
  "org.automorph" %% "automorph-default" % "@PROJECT_VERSION@"
)
```

#### Gradle

```yaml
implementation group: 'org.automorph', name: 'automorph-default_3', version: '@PROJECT_VERSION@'
```

### Server

Expose the API instance for remote calls using JSON-RPC over HTTP(S).

```scala
import automorph.Default
import io.circe.generic.auto.*
import java.net.URI
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}

// Define a remote API
trait Api {
  def hello(some: String, n: Int): Future[String]
}

// Create server implementation of the remote API
class ApiImpl {
  def hello(some: String, n: Int): Future[String] =
    Future(s"Hello $some $n!")
}
val api = new ApiImpl

// Configure JSON-RPC HTTP & WebSocket server to listen on port 9000 for requests to '/api'
val server = Default.rpcServer(9000, "/api")

// Expose the server API implementation to be accessible remotely
val boundServer = server.bind(api)

Await.ready(for {
  // Start the JSON-RPC server
  activeServer <- boundServer.init()

  // Stop the JSON-RPC server
  _ <- activeServer.close()
} yield (), Duration.Inf)
```

### Static client

Call the remote API instance via proxy created from API type using JSON-RPC over HTTP(S).

```scala
import automorph.Default
import io.circe.generic.auto.*
import java.net.URI
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}

// Define a remote API
trait Api {
  def hello(some: String, n: Int): Future[String]
}

// Configure JSON-RPC HTTP client to send POST requests to 'http://localhost:9000/api'
val client = Default.rpcClient(new URI("http://localhost:9000/api"))

// Create proxy for the remote API to be accessible locally
val remoteApi = client.bind[Api]

Await.ready(for {
  // Initialize the JSON-RPC client
  activeClient <- client.init()

  // Call the remote API function statically
  result <- remoteApi.hello("world", 1)
  _ = println(result)

  // Close the JSON-RPC client
  _ <- activeClient.close()
} yield (), Duration.Inf)
```

### Dynamic client

Call the remote API dynamically without API type definition using JSON-RPC over HTTP(S).

```scala
import automorph.Default
import java.net.URI
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}

// Configure JSON-RPC HTTP client to send POST requests to 'http://localhost:9000/api'
val client = Default.rpcClient(new URI("http://localhost:9000/api"))

Await.ready(for {
  // Initialize the JSON-RPC client
  activeClient <- client.init()

  // Call the remote API function dynamically
  result <- client.call[String]("hello")("some" -> "world", "n" -> 1)
  _ = println(result)

  // Close the JSON-RPC client
  _ <- activeClient.close()
} yield (), Duration.Inf)
```

