// Translate RPC errors to exceptions on the client side.
package examples.errorhandling

import automorph.{RpcClient, Default}
import java.net.URI
import java.sql.SQLException
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}

private[examples] object ClientErrorMapping {

  @scala.annotation.nowarn
  def main(arguments: Array[String]): Unit = {

    // Define a remote API
    trait Api {
      def hello(n: Int): Future[String]
    }

    // Create server implementation of the remote API
    val service = new Api {
      def hello(n: Int): Future[String] =
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

    Await.result(
      for {
        // Initialize JSON-RPC HTTP & WebSocket server listening on port 9000 for requests to '/api'
        server <- Default.rpcServer(9000, "/api").bind(service).init()

        // Initialize custom JSON-RPC HTTP client
        client <- RpcClient.transport(clientTransport).rpcProtocol(rpcProtocol).init()
        remoteApi = client.bind[Api]

        // Call the remote API function via a local proxy and fail with SQLException
        error <- remoteApi.hello(1).failed
        _ = println(error)

        // Close the RPC client and server
        _ <- client.close()
        _ <- server.close()
      } yield (),
      Duration.Inf,
    )
  }
}
