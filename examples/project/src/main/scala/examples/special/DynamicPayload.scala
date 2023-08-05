package examples.special

import automorph.Default
import io.circe.Json
import java.net.URI

private[examples] object DynamicPayload {
  @scala.annotation.nowarn
  def main(arguments: Array[String]): Unit = {

    // Define client view of a remote API
    trait Api {
      def hello(some: String, n: Json): Json
    }

    // Create server implementation of the remote API
    class ApiImpl {
      def hello(some: Json, n: Int): Json =
        if (some.isString) {
          val value = some.as[String].toTry.get
          Json.fromString(s"Hello $value $n!")
        } else {
          Json.fromValues(Seq(some, Json.fromInt(n)))
        }
    }
    val api = new ApiImpl

    // Initialize JSON-RPC HTTP & WebSocket server listening on port 9000 for PUT requests to '/api'
    val server = Default.rpcServerSync(9000, "/api").bind(api).init()

    // Initialize JSON-RPC HTTP & WebSocket client for sending PUT requests to 'http://localhost:9000/api'
    val client = Default.rpcClientSync(new URI("http://localhost:9000/api")).init()

    // Call the remote API function statically
    val remoteApi = client.bind[Api]
    println(
      remoteApi.hello("world", Json.fromInt(1))
    )

    // Call the remote API function dynamically
    println(
      client.call[Seq[Int]]("hello")("some" -> Json.fromInt(0), "n" -> 1)
    )

    // Close the RPC client
    client.close()

    // Close the RPC server
    server.close()
  }
}
