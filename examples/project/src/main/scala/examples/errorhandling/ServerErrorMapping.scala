package examples.errorhandling

import automorph.protocol.jsonrpc.ErrorType.InvalidRequest
import automorph.protocol.jsonrpc.JsonRpcException
import automorph.{Default, RpcServer}
import java.net.URI
import java.sql.SQLException
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}

private[examples] object ServerErrorMapping {
  @scala.annotation.nowarn
  def main(arguments: Array[String]): Unit = {

    // Define a remote API
    trait Api {
      def hello(some: String, n: Int): Future[String]
    }

    // Create server implementation of the remote API
    val api = new Api {
      def hello(some: String, n: Int): Future[String] =
        if (n >= 0) {
          Future.failed(new SQLException("Invalid request"))
        } else {
          Future.failed(JsonRpcException("Application error", 1))
        }
    }

    // Customize remote API server exception to RPC error mapping
    val rpcProtocol = Default.rpcProtocol[Default.ServerContext].mapException(_ match {
      case _: SQLException => InvalidRequest
      case error => Default.rpcProtocol.mapException(error)
    })

    // Create HTTP & WebSocket server transport listening on port 9000 for requests to '/api'
    val serverTransport = Default.serverTransport(9000, "/api")

    Await.ready(for {
      // Initialize custom JSON-RPC HTTP & WebSocket server
      server <- RpcServer.transport(serverTransport).rpcProtocol(rpcProtocol).bind(api).init()

      // Initialize JSON-RPC HTTP client for sending POST requests to 'http://localhost:9000/api'
      client <- Default.rpcClient(new URI("http://localhost:9000/api")).init()
      remoteApi = client.bind[Api]

      // Call the remote API function via a local proxy an fail with InvalidRequestException
      error <- remoteApi.hello("world", 1).failed
      _ = println(error)

      // Call the remote API function via a local proxy and fail with RuntimeException
      error <- remoteApi.hello("world", -1).failed
      _ = println(error)

      // Close the RPC client and server
      _ <- client.close()
      _ <- server.close()
    } yield (), Duration.Inf)
  }
}
