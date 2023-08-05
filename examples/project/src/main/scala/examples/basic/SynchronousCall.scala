package examples.basic

import automorph.Default
import java.net.URI

private[examples] object SynchronousCall {
  @scala.annotation.nowarn
  def main(arguments: Array[String]): Unit = {

    // Define a remote API
    trait Api {
      def hello(some: String, n: Int): String
    }

    // Create server implementation of the remote API
    val api = new Api {
      def hello(some: String, n: Int): String =
        s"Hello $some $n!"
    }

    // Initialize JSON-RPC HTTP & WebSocket server listening on port 9000 for POST requests to '/api'
    val server = Default.rpcServerSync(9000, "/api").bind(api).init()

    // Initialize JSON-RPC HTTP client for sending POST requests to 'http://localhost:9000/api'
    val client = Default.rpcClientSync(new URI("http://localhost:9000/api")).init()

    // Call the remote API function statically
    val remoteApi = client.bind[Api]
    println(
      remoteApi.hello("world", 1)
    )

    // Call the remote API function dynamically
    println(
      client.call[String]("hello")("some" -> "world", "n" -> 1)
    )

    // Close the RPC client
    client.close()

    // Close the RPC server
    server.close()
  }
}
