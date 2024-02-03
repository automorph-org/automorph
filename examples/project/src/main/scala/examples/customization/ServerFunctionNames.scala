package examples.customization

import automorph.Default
import automorph.system.IdentitySystem
import java.net.URI
import scala.util.Try

private[examples] object ServerFunctionNames {

  @scala.annotation.nowarn
  def main(arguments: Array[String]): Unit = {

    // Define client view of a remote API
    trait Api {
      def hello(some: String, n: Int): String

      def hi(some: String, n: Int): String
    }

    // Create server implementation of the remote API
    class ApiImpl {
      def hello(some: String, n: Int): String =
        s"Hello $some $n!"

      def sum(numbers: List[Double]): Double =
        numbers.sum

      def hidden(): String =
        ""
    }
    val service = new ApiImpl

    // Customize served API to RPC function name mapping
    val mapName = (name: String) => name match {
      // 'hello' is exposed both as 'hello' and 'hi'
      case "hello" => Seq("hello", "hi")

      // 'hidden' is not exposed
      case "hidden" => Seq.empty

      // 'sum' is exposed as 'test.sum'
      case other => Seq(s"test.$other")
    }

    // Initialize JSON-RPC HTTP & WebSocket server listening on port 9000 for requests to '/api'
    val server = Default.rpcServerCustom(IdentitySystem(), 9000, "/api").bind(service, mapName).init()

    // Initialize JSON-RPC HTTP client for sending POST requests to 'http://localhost:9000/api'
    val client = Default.rpcClientCustom(IdentitySystem(), new URI("http://localhost:9000/api")).init()

    // Call the remote API function via a local proxy
    val remoteApi = client.bind[Api]
    println(
      remoteApi.hello("world", 1)
    )
    println(
      remoteApi.hi("world", 1)
    )

    // Call the remote API function dynamically without an API trait
    println(
      client.call[Double]("test.sum")("numbers" -> List(1, 2, 3))
    )

    // Call the remote API function dynamically and fail with FunctionNotFoundException
    println(Try(
      client.call[String]("hidden")()
    ).failed.get)

    // Close the RPC client and server
    client.close()
    server.close()
  }
}
