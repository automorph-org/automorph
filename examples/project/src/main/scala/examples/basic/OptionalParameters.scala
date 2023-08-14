package examples.basic

import automorph.Default
import automorph.system.IdentitySystem

import java.net.URI

private[examples] object OptionalParameters {
  @scala.annotation.nowarn
  def main(arguments: Array[String]): Unit = {

    // Define client view of a remote API
    trait Api {
      def hello(some: String): String
    }

    // Create server implementation of the remote API
    class ApiImpl {
      def hello(some: String, n: Option[Int]): String =
        s"Hello $some ${n.getOrElse(0)}!"

      def hi(some: Option[String])(n: Int): String =
        s"Hi ${some.getOrElse("all")} $n!"
    }
    val api = new ApiImpl

    // Initialize JSON-RPC HTTP & WebSocket server listening on port 9000 for POST requests to '/api'
    val server = Default.rpcServerCustom(IdentitySystem(), 9000, "/api").bind(api).init()

    // Initialize JSON-RPC HTTP client for sending POST requests to 'http://localhost:9000/api'
    val client = Default.rpcClientCustom(IdentitySystem(), new URI("http://localhost:9000/api")).init()

    // Call the remote API function statically
    val remoteApi = client.bind[Api]
    println(
      remoteApi.hello("world")
    )

    // Call the remote API function dynamically
    println(
      client.call[String]("hi")("n" -> 1) // String
    )

    // Close the RPC client
    client.close()

    // Close the RPC server
    server.close()
  }
}
