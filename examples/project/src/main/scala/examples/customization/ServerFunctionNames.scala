package examples.customization

import automorph.Default
import java.net.URI
import scala.util.Try

private[examples] case object ServerFunctionNames {
  @scala.annotation.nowarn
  def main(arguments: Array[String]): Unit = {

    // Create server API instance
    class ServerApi {
      // Exposed both as 'hello' and 'hi'
      def hello(some: String, n: Int): String =
        s"Hello $some $n!"

      // Exposed as 'test.sum'
      def sum(numbers: List[Double]): Double =
        numbers.sum

      // Not exposed
      def hidden(): String =
        ""
    }
    val api = new ServerApi

    // Customize exposed API to RPC function name mapping
    val mapName = (name: String) => name match {
      case "hello" => Seq("hello", "hi")
      case "hidden" => Seq.empty
      case other => Seq(s"test.$other")
    }

    // Initialize JSON-RPC HTTP & WebSocket server listening on port 7000 for requests to '/api'
    val server = Default.rpcServerSync(7000, "/api").bind(api, mapName).init()

    // Define client view of the remote API
    trait ClientApi {
      def hello(some: String, n: Int): String

      def hi(some: String, n: Int): String
    }

    // Initialize JSON-RPC HTTP client sending POST requests to 'http://localhost:7000/api'
    val client = Default.rpcClientSync(new URI("http://localhost:7000/api")).init()

    // Call the remote API function statically
    val remoteApi = client.bind[ClientApi]
    println(
      remoteApi.hello("world", 1)
    )
    println(
      remoteApi.hi("world", 1)
    )

    // Call the remote API function dynamically
    println(
      client.call[Double]("test.sum")("numbers" -> List(1, 2, 3))
    )

    // Call the remote API function dynamically and fail with FunctionNotFoundException
    println(Try(
      client.call[String]("hidden")()
    ).failed.get)

    // Close the RPC client
    client.close()

    // Close the RPC server
    server.close()
  }
}
