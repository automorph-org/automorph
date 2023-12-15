---
sidebar_position: 2
---

# Quickstart

Expose and call a remote JSON-RPC API over HTTP.

Please see the library functionality [overview](https://automorph.org/docs/Overview), component [architecture](https://automorph.org/docs/Architecture) additional [examples](https://automorph.org/docs/Examples) and [API](https://automorph.org/api/automorph.html) documentation for more information.


## Script

Download and run a quickstart example using [Scala CLI](https://scala-cli.virtuslab.org):

```shell
scala-cli "https://automorph.org/examples/src/main/scala/examples/Quickstart.scala"
```


## Existing project

### Build

Add the following dependency to your build configuration:

#### SBT

```scala
libraryDependencies += "org.automorph" %% "automorph-default" % "@PROJECT_VERSION@"
```

#### Gradle

```yaml
implementation group: 'org.automorph', name: 'automorph-default_3', version: '@PROJECT_VERSION@'
```

### Server

Expose an API instance for remote calls using JSON-RPC over HTTP(S).

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
val apiServer = server.bind(api)

Await.ready(for {
  // Start the JSON-RPC server
  activeServer <- apiServer.init()

  // Stop the JSON-RPC server
  _ <- activeServer.close()
} yield (), Duration.Inf)
```

### Client

Call a remote API using JSON-RPC over HTTP(S) via a type-safe local proxy created from the API trait or
dynamically without an API trait.

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

// Create a type-safe local proxy for the remote API from the API trait
val remoteApi = client.bind[Api]

Await.ready(for {
  // Initialize the JSON-RPC client
  activeClient <- client.init()

  // Call the remote API function via the local proxy
  result <- remoteApi.hello("world", 1)
  _ = println(result)

  // Call the remote API function dynamically without an API trait
  result <- activeClient.call[String]("hello")("some" -> "world", "n" -> 1)
  _ = println(result)

  // Close the JSON-RPC client
  _ <- activeClient.close()
} yield (), Duration.Inf)
```


## New project

### Template

Create an [SBT](https://www.scala-sbt.org/) project containing a quickstart example from a
[Giter8](http://www.foundweekends.org/giter8/) template:

```shell
sbt new automorph-org/automorph.g8
cd automorph-example
sbt run
```

Customize the example by editing `src/main/scala/examples/Quickstart.scala` and `build.sbt`.

Application logs are saved to `target/main.log` using the `LOG_LEVEL` environment variable to set a log level (`ERROR`, `WARN`, `INFO`, `DEBUG`, `TRACE`).


### Examples

Clone the [example project](@REPOSITORY_URL@/tree/main/examples/project) and run any of the examples:

```shell
git clone --depth 1 @REPOSITORY_URL@
cd automorph/examples/project
sbt run
```

Customize the [examples](@REPOSITORY_URL@/blob/main/examples/project/src/main/scala/examples) by:
- Integrating with preferred platforms by including additional plugins
- Configuring RPC client, server or endpoint properties
- Removing unused examples and build dependencies
