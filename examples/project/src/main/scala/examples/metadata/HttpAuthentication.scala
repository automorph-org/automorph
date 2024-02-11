//> using dep org.automorph::automorph-default:@PROJECT_VERSION@
//> using dep ch.qos.logback:logback-classic:@LOGGER_VERSION@
package examples.metadata

import automorph.Default
import automorph.Default.{ClientContext, ServerContext}
import automorph.system.IdentitySystem
import java.net.URI
import scala.util.Try

private[examples] object HttpAuthentication {

  @scala.annotation.nowarn
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
        httpRequest.authorization("Bearer") match {
          case Some("valid") => s"Note: $message!"
          case _ => throw new IllegalAccessException("Authentication failed")
        }
    }
    val service = new Service

    // Initialize JSON-RPC HTTP & WebSocket server listening on port 9000 for requests to '/api'
    val server = Default.rpcServerCustom(IdentitySystem(), 9000, "/api").bind(service).init()

    // Initialize JSON-RPC HTTP client for sending POST requests to 'http://localhost:9000/api'
    val client = Default.rpcClientCustom(IdentitySystem(), new URI("http://localhost:9000/api")).init()
    val remoteApi = client.bind[Api]

    {
      // Create client request context containing invalid HTTP authentication
      implicit val validAuthentication: ClientContext = client.context
        .authorization("Bearer", "valid")

      // Call the remote API function via a local proxy using valid authentication
      println(
        remoteApi.hello("test")
      )

      // Call the remote API function dynamically using valid authentication
      println(
        client.call[String]("hello")("message" -> "test")
      )
    }

    {
      // Create client request context containing invalid HTTP authentication
      implicit val invalidAuthentication: ClientContext = client.context
        .headers("X-Authentication" -> "unsupported")

      // Call the remote API function statically using invalid authentication
      println(Try(
        remoteApi.hello("test")
      ).failed.get)

      // Call the remote API function dynamically using invalid authentication
      println(Try(
        client.call[String]("hello")("message" -> "test")
      ).failed.get)
    }

    // Close the RPC client and server
    client.close()
    server.close()
  }
}
