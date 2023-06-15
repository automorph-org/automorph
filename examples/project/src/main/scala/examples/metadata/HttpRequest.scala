package examples.metadata

import automorph.Default
import automorph.Default.{ClientContext, ServerContext}
import java.net.URI

private[examples] case object HttpRequest {
  @scala.annotation.nowarn
  def main(arguments: Array[String]): Unit = {

    // Create server API instance
    class ServerApi {

      // Accept HTTP request context provided by the server message transport plugin
      def hello(message: String)(implicit httpRequest: ServerContext): String =
        Seq(
          Some(message),
          httpRequest.path,
          httpRequest.header("X-Test")
        ).flatten.mkString(",")
    }
    val api = new ServerApi

    // Initialize JSON-RPC HTTP & WebSocket server listening on port 7000 for requests to '/api'
    val server = Default.rpcServerSync(7000, "/api").bind(api).init()

    // Define client view of the remote API
    trait ClientApi {

      // Accept HTTP request context consumed by the client message transport plugin
      def hello(message: String)(implicit http: ClientContext): String
    }

    // Initialize JSON-RPC HTTP client sending POST requests to 'http://localhost:7000/api'
    val client = Default.rpcClientSync(new URI("http://localhost:7000/api")).init()

    // Create client request context specifying HTTP request metadata
    implicit val httpRequest: ClientContext = client.context
      .parameters("test" -> "value")
      .headers("X-Test" -> "value", "Cache-Control" -> "no-cache")
      .cookies("Example" -> "value")
      .authorizationBearer("value")

    // Call the remote API function statically using implicitly given HTTP request metadata
    val remoteApi = client.bind[ClientApi]
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
