package examples.integration

import automorph.protocol.WebRpcProtocol
import automorph.{RpcClient, Default, RpcServer}
import java.net.URI
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}

private[examples] object RpcProtocol {
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

    // Create a server Web-RPC protocol plugin with '/api' as URL path prefix
    val serverRpcProtocol = WebRpcProtocol[Default.Node, Default.Codec, Default.ServerContext](
      Default.messageCodec, "/api"
    )

    // Create HTTP & WebSocket server transport listening on port 9000 for requests to '/api'
    val serverTransport = Default.serverTransport(9000, "/api")

    // Create a client Web-RPC protocol plugin with '/api' path prefix
    val clientRpcProtocol = WebRpcProtocol[Default.Node, Default.Codec, Default.ClientContext](
      Default.messageCodec, "/api"
    )

    // Create HTTP & WebSocket client transport sending POST requests to 'http://localhost:9000/api'
    val clientTransport = Default.clientTransport(new URI("http://localhost:9000/api"))

    Await.ready(for {
      // Initialize custom JSON-RPC HTTP & WebSocket server
      server <- RpcServer.transport(serverTransport).rpcProtocol(serverRpcProtocol).bind(api).init()

      // Initialize custom JSON-RPC HTTP client
      client <- RpcClient.transport(clientTransport).rpcProtocol(clientRpcProtocol).init()
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
