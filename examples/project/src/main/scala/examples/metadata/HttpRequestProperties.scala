// Attach and extract URL query parameters and HTTP headers to/from a remote API request.
//> using scala @SCALA_VERSION@
//> using dep org.automorph::automorph-default:@AUTOMORPH_VERSION@
//> using dep ch.qos.logback:logback-classic:@LOGBACK_VERSION@
package examples.metadata

import automorph.Default
import automorph.Default.{ClientContext, ServerContext}
import automorph.system.IdentitySystem
import java.net.URI

private[examples] object HttpRequestProperties {

  def main(arguments: Array[String]): Unit = {

    // Define client view of a remote API
    trait Api {
      // Accept HTTP request context consumed by the client transport plugin
      def hello(message: String)(implicit http: ClientContext): String
    }

    // Create server implementation of the remote API
    class Service {
      // Accept HTTP request context provided by the server message transport plugin
      def hello(message: String)(implicit httpRequest: ServerContext): String =
        Seq(
          Some(message),
          httpRequest.path,
          httpRequest.header("X-Test"),
        ).flatten.mkString(",")
    }
    val service = new Service

    // Initialize JSON-RPC HTTP & WebSocket server listening on port 9000 for requests to '/api'
    val server = Default.rpcServerCustom(IdentitySystem(), 9000, "/api").service(service).init()

    // Initialize JSON-RPC HTTP client for sending POST requests to 'http://localhost:9000/api'
    val client = Default.rpcClientCustom(IdentitySystem(), new URI("http://localhost:9000/api")).init()

    // Create HTTP request context consumed by the client transport plugin
    implicit val httpRequest: ClientContext = client.context
      .parameters("test" -> "value")
      .headers("X-Test" -> "value", "Cache-Control" -> "no-cache")
      .cookies("Example" -> "value")
      .authorization("Bearer", "value")

    // Call the remote API function via a local proxy using implicitly given HTTP request metadata
    val remoteApi = client.proxy[Api]
    println(
      remoteApi.hello("test")
    )

    // Call the remote API function dynamically using implicitly given HTTP request metadata
    println(
      client.call[String]("hello")("message" -> "test")
    )

    // Close the RPC client and server
    client.close()
    server.close()
    ()
  }
}
