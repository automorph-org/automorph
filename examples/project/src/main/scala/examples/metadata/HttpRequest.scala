package examples.metadata

import automorph.Default
import automorph.Default.{ClientContext, ServerContext}
import automorph.system.IdentitySystem
import java.net.URI

private[examples] object HttpRequest {
  @scala.annotation.nowarn
  def main(arguments: Array[String]): Unit = {

    // Define client view of a remote API
    trait Api {
      // Accept HTTP request context consumed by the client message transport plugin
      def hello(message: String)(implicit http: ClientContext): String
    }

    // Create server implementation of the remote API
    class ApiImpl {
      // Accept HTTP request context provided by the server message transport plugin
      def hello(message: String)(implicit httpRequest: ServerContext): String =
        Seq(
          Some(message),
          httpRequest.path,
          httpRequest.header("X-Test")
        ).flatten.mkString(",")
    }
    val api = new ApiImpl

    // Initialize JSON-RPC HTTP & WebSocket server listening on port 9000 for requests to '/api'
    val server = Default.rpcServerCustom(IdentitySystem(), 9000, "/api").bind(api).init()

    // Initialize JSON-RPC HTTP client for sending POST requests to 'http://localhost:9000/api'
    val client = Default.rpcClientCustom(IdentitySystem(), new URI("http://localhost:9000/api")).init()

    // Create client request context specifying HTTP request metadata
    implicit val httpRequest: ClientContext = client.context
      .parameters("test" -> "value")
      .headers("X-Test" -> "value", "Cache-Control" -> "no-cache")
      .cookies("Example" -> "value")
      .authorization("Bearer", "value")

    // Call the remote API function statically using implicitly given HTTP request metadata
    val remoteApi = client.bind[Api]
    println(
      remoteApi.hello("test")
    )

    // Call the remote API function dynamically using implicitly given HTTP request metadata
    println(
      client.call[String]("hello")("message" -> "test")
    )

    // Close the RPC client
    client.close()

    // Close the RPC server
    server.close()
  }
}
