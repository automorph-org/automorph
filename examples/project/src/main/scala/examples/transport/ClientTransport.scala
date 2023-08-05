package examples.transport

import automorph.{RpcClient, Default}
import automorph.transport.http.client.UrlClient
import java.net.URI

private[examples] case object ClientTransport {
  @scala.annotation.nowarn
  def main(arguments: Array[String]): Unit = {

    // Define a remote API
    trait Api {
      def hello(some: String, n: Int): String
    }

    // Create server implementation of the remote API
    val api = new Api {
      override def hello(some: String, n: Int): String = s"Hello $some $n!"
    }

    // Initialize JSON-RPC HTTP & WebSocket server listening on port 80 for requests to '/api'
    val server = Default.rpcServerSync(9000, "/api").bind(api).init()

    // Create standard JRE HTTP client message transport sending POST requests to 'http://localhost:9000/api'
    val clientTransport = UrlClient(Default.effectSystemSync, new URI("http://localhost:9000/api"))

    // Setup JSON-RPC HTTP client
    val client = RpcClient.transport(clientTransport).rpcProtocol(Default.rpcProtocol).init()

    // Call the remote API function via proxy
    val remoteApi = client.bind[Api]
    println(
      remoteApi.hello("world", 1)
    )

    // Close the RPC client
    client.close()

    // Close the RPC server
    server.close()
  }
}
