package examples.customization

import automorph.Default
import java.net.URI

private[examples] object ClientFunctionNames {
  @scala.annotation.nowarn
  def main(arguments: Array[String]): Unit = {

    // Define client view of a remote API
    trait Api {
      def hello(some: String, n: Int): String

      // Invoked as 'hello'
      def hi(some: String, n: Int): String
    }

    // Create server implementation of the remote API
    class ApiImpl {
      // Exposed both as 'hello' and 'hi'
      def hello(some: String, n: Int): String =
        s"Hello $some $n!"
    }
    val api = new ApiImpl

    // Initialize JSON-RPC HTTP & WebSocket server listening on port 9000 for requests to '/api'
    val server = Default.rpcServerSync(9000, "/api").bind(api).init()

    // Initialize JSON-RPC HTTP client for sending POST requests to 'http://localhost:9000/api'
    val client = Default.rpcClientSync(new URI("http://localhost:9000/api")).init()

    // Customize invoked API to RPC function name mapping
    val mapName = (name: String) => name match {
      case "hi" => "hello"
      case other => other
    }

    // Call the remote API function
    val remoteApi = client.bind[Api](mapName)
    println(
      remoteApi.hello("world", 1)
    )
    println(
      remoteApi.hi("world", 1)
    )

    // Close the RPC client
    client.close()

    // Close the RPC server
    server.close()
  }
}
