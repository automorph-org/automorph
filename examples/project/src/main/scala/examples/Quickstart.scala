//> using scala 3.3.1
//> using dep org.automorph::automorph-default:0.2.5
//> using dep ch.qos.logback:logback-classic:1.4.14
package examples

import automorph.Default
import java.net.URI
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}

private[examples] object Quickstart {

  // Serve an API implementation and call it remotely using JSON-RPC over HTTP(S).
  @scala.annotation.nowarn
  def main(arguments: Array[String]): Unit = {
    import io.circe.generic.auto.*

    // Define a remote API
    trait Api {
      def hello(n: Int): Future[String]
    }

    // Create server implementation of the remote API
    class Service {
      def hello(n: Int): Future[String] =
        Future(s"Hello world $n")
    }
    val service = new Service

    // Configure JSON-RPC HTTP & WebSocket server to listen on port 9000 for requests to '/api'
    val inactiveServer = Default.rpcServer(9000, "/api")

    // Serve the API implementation to be called remotely
    val apiServer = inactiveServer.bind(service)

    // Configure JSON-RPC HTTP client to send POST requests to 'http://localhost:9000/api'
    val inactiveClient = Default.rpcClient(new URI("http://localhost:9000/api"))

    // Create a type-safe local proxy for the remote API from the API trait
    val remoteApi = inactiveClient.bind[Api]

    val run = for {
      // Start the JSON-RPC server
      server <- apiServer.init()

      // Initialize the JSON-RPC client
      client <- inactiveClient.init()

      // Call the remote API function via the local proxy
      result <- remoteApi.hello(1)
      _ = println(result)

      // Call the remote API function dynamically not using the API trait
      result <- client.call[String]("hello")("n" -> 1)
      _ = println(result)

      // Close the JSON-RPC client
      _ <- client.close()

      // Stop the JSON-RPC server
      _ <- server.close()
    } yield ()
    Await.result(run, Duration.Inf)
  }
}
