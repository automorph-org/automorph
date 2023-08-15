package examples.special

import automorph.{RpcClient, Default}
import java.net.URI
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}

private[examples] object PositionalArguments {
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

    // Configure JSON-RPC to pass arguments by position instead of by name
    val rpcProtocol = Default.rpcProtocol[Default.ClientContext].namedArguments(false)

    // Create HTTP client transport sending POST requests to 'http://localhost:9000/api'
    val clientTransport = Default.clientTransport(new URI("http://localhost:9000/api"))

    Await.ready(for {
      // Initialize JSON-RPC HTTP & WebSocket server listening on port 9000 for requests to '/api'
      server <- Default.rpcServer(9000, "/api").bind(api).init()

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
  }
}
