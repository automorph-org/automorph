// Serve an API implementation and call it remotely using JSON-RPC over HTTP(S).
//> using scala 3.6.2
//> using dep org.automorph::automorph-default:0.2.9
//> using dep ch.qos.logback:logback-classic:1.5.12
package examples

import automorph.Default
import io.circe.generic.auto.*
import java.net.URI
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}

private[examples] object Quickstart {

  @scala.annotation.nowarn
  def main(arguments: Array[String]): Unit = {

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
    val server = Default.rpcServer(9000, "/api")

    // Expose the server API implementation to be called remotely
    val apiServer = server.bind(service)

    // Configure JSON-RPC HTTP client to send POST requests to 'http://localhost:9000/api'
    val client = Default.rpcClient(new URI("http://localhost:9000/api"))

    // Create a type-safe local proxy for the remote API from the API trait
    val remoteApi = client.bind[Api]

    Await.ready(for {
      // Start the JSON-RPC server
      activeServer <- apiServer.init()

      // Initialize the JSON-RPC client
      activeClient <- client.init()

      // Call the remote API function via the local proxy
      result <- remoteApi.hello(1)
      _ = println(result)

      // Call the remote API function dynamically without using the API trait
      result <- activeClient.call[String]("hello")("n" -> 1)
      _ = println(result)

      // Close the JSON-RPC client
      _ <- activeClient.close()

      // Stop the JSON-RPC server
      _ <- activeServer.close()
    } yield (), Duration.Inf)
    ()
  }
}
