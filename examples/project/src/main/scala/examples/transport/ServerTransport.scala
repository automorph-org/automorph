package examples.transport

import automorph.{Default, RpcServer}
import automorph.transport.http.server.NanoServer
import java.net.URI

private[examples] case object ServerTransport {
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

    // Create NanoHTTPD HTTP & WebSocket server transport listening on port 9000 for requests to '/api'
    val serverTransport = NanoServer(Default.effectSystemSync, 9000, "/api")

    // Initialize JSON-RPC HTTP & WebSocket server
    val server = RpcServer.transport(serverTransport).rpcProtocol(Default.rpcProtocol).bind(api).init()

    // Initialize JSON-RPC HTTP client for sending POST requests to 'http://localhost:9000/api'
    val client = Default.rpcClientSync(new URI("http://localhost:9000/api"))

    // Call the remote API function
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
