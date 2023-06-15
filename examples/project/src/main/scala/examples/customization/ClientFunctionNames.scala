package examples.customization

import automorph.Default
import java.net.URI

private[examples] case object ClientFunctionNames {
  @scala.annotation.nowarn
  def main(arguments: Array[String]): Unit = {

    // Create server API instance
    class ServerApi {
      // Exposed both as 'hello' and 'hi'
      def hello(some: String, n: Int): String =
        s"Hello $some $n!"
    }
    val api = new ServerApi

    // Initialize JSON-RPC HTTP & WebSocket server listening on port 7000 for requests to '/api'
    val server = Default.rpcServerSync(7000, "/api").bind(api).init()

    // Define client view of the remote API
    trait ClientApi {
      def hello(some: String, n: Int): String

      // Invoked as 'hello'
      def hi(some: String, n: Int): String
    }

    // Initialize JSON-RPC HTTP client sending POST requests to 'http://localhost:7000/api'
    val client = Default.rpcClientSync(new URI("http://localhost:7000/api")).init()

    // Customize invoked API to RPC function name mapping
    val mapName = (name: String) => name match {
      case "hi" => "hello"
      case other => other
    }

    // Call the remote API function
    val remoteApi = client.bind[ClientApi](mapName)
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
