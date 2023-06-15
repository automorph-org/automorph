package examples.special

import automorph.Default
import io.circe.Json
import java.net.URI

private[examples] case object DynamicPayload {
  @scala.annotation.nowarn
  def main(arguments: Array[String]): Unit = {

    // Create server API instance
    class ServerApi {
      def hello(some: Json, n: Int): Json =
        if (some.isString) {
          val value = some.as[String].toTry.get
          Json.fromString(s"Hello $value $n!")
        } else {
          Json.fromValues(Seq(some, Json.fromInt(n)))
        }
    }
    val api = new ServerApi

    // Initialize JSON-RPC HTTP & WebSocket server listening on port 7000 for PUT requests to '/api'
    val server = Default.rpcServerSync(7000, "/api").bind(api).init()

    // Define client view of the remote API
    trait ClientApi {
      def hello(some: String, n: Json): Json
    }

    // Initialize JSON-RPC HTTP & WebSocket client sending PUT requests to 'http://localhost:7000/api'
    val client = Default.rpcClientSync(new URI("http://localhost:7000/api")).init()

    // Call the remote API function statically
    val remoteApi = client.bind[ClientApi]
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
