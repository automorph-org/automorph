package examples.basic

import automorph.Default
import automorph.transport.http.HttpMethod
import java.net.URI
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}

private[examples] object AsynchronousCall {
  @scala.annotation.nowarn
  def main(arguments: Array[String]): Unit = {

    // Helper function to evaluate Futures
    def run[T](effect: Future[T]): T = Await.result(effect, Duration.Inf)

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

    // Initialize JSON-RPC HTTP client for sending PUT requests to 'http://localhost:9000/api'
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

    // Close the RPC client
    run(client.close())

    // Close the RPC server
    run(server.close())
  }
}
