package examples.basic

import automorph.Default
import automorph.transport.http.HttpMethod.{Post, Put}
import java.net.URI
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}

private[examples] object AsynchronousCall {
  @scala.annotation.nowarn
  def main(arguments: Array[String]): Unit = {

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
      server <- Default.rpcServer(9000, "/api", Seq(Post, Put)).bind(api).init()

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
  }
}
