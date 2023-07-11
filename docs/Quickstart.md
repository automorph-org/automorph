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

Application logs are saved to `target/main.log` and the log level can be adjusted by setting the `LOG_LEVEL` environment variable to a desired value (`ERROR`, `WARN`, `INFO`, `DEBUG`, `TRACE`).


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

// Helper function to evaluate Futures
def run[T](effect: Future[T]): T = Await.result(effect, Duration.Inf)

// Create server API instance
class ServerApi {
  def hello(some: String, n: Int): Future[String] =
    Future(s"Hello $some $n!")
}
val api = new ServerApi

// Initialize JSON-RPC HTTP & WebSocket server listening on port 7000 for requests to '/api'
val server = run(
  Default.rpcServerAsync(7000, "/api").bind(api).init()
)

// Close the RPC server
run(server.close())
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

// Helper function to evaluate Futures
def run[T](effect: Future[T]): T = Await.result(effect, Duration.Inf)

// Define client view of the remote API
trait ClientApi {
  def hello(some: String, n: Int): Future[String]
}

// Initialize JSON-RPC HTTP & WebSocket client sending POST requests to 'http://localhost:7000/api'
val client = run(
  Default.rpcClientAsync(new URI("http://localhost:7000/api")).init()
)

// Call the remote API function statically
val remoteApi = client.bind[ClientApi]
println(run(
  remoteApi.hello("world", 1)
))

// Close the RPC client
run(client.close())
```

### Dynamic client

Call the remote API dynamically without API type definition using JSON-RPC over HTTP(S).

```scala
import automorph.Default
import java.net.URI
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}

// Helper function to evaluate Futures
def run[T](effect: Future[T]): T = Await.result(effect, Duration.Inf)

// Initialize JSON-RPC HTTP client sending POST requests to 'http://localhost:7000/api'
val client = run(
  Default.rpcClientAsync(new URI("http://localhost:7000/api")).init()
)

// Call the remote API function dynamically
println(run(
  client.call[String]("hello")("some" -> "world", "n" -> 1)
))

// Close the RPC client
run(client.close())
```

