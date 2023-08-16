package examples.errorhandling

import automorph.{RpcClient, Default}
import java.net.URI
import java.sql.SQLException
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}

private[examples] object ClientErrors {
  @scala.annotation.nowarn
  def main(arguments: Array[String]): Unit = {

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
  }
}
