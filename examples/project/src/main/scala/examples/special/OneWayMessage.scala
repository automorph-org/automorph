package examples.special

import automorph.Default
import java.net.URI
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}

private[examples] case object OneWayMessage {
  @scala.annotation.nowarn
  def main(arguments: Array[String]): Unit = {

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

    // Define client view of the remote API
    trait ClientApi {
      def hello(some: String, n: Int): Future[String]
    }

    // Initialize JSON-RPC HTTP client sending POST requests to 'http://localhost:7000/api'
    val client = run(
      Default.rpcClientAsync(new URI("http://localhost:7000/api")).init()
    )

    // Call the remote API function dynamically without expecting a response
    run(
      client.tell("hello")("some" -> "world", "n" -> 1)
    )

    // Close the RPC client
    run(client.close())

    // Close the RPC server
    run(server.close())
  }
}
