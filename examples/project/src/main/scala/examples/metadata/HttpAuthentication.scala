//> using dep org.automorph::automorph-default:@AUTOMORPH_VERSION@
//> using dep ch.qos.logback:logback-classic:@LOGBACK_VERSION@
package examples.metadata

import automorph.{Default, RpcCall}
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

      def public(): String
    }

    // Create server implementation of the remote API
    class Service {
      def hello(message: String): String =
        s"Hello world $message"

      def public(): String =
        "OK"
    }
    val service = new Service

    // Customize RPC request filtering to reject unauthenticated calls to non-public functions
    val filterCall = (call: RpcCall[ServerContext]) => call.context.authorization("Bearer") match {
      case Some("valid") => None
      case _ if call.function == "public" => None
      case _ => Some(new IllegalAccessException("Authentication failed"))
    }

    // Initialize JSON-RPC HTTP & WebSocket server with API with custom request filter
    val server = Default.rpcServerCustom(IdentitySystem(), 9000, "/api").bind(service).callFilter(filterCall).init()

    // Initialize JSON-RPC HTTP client for sending POST requests to 'http://localhost:9000/api'
    val client = Default.rpcClientCustom(IdentitySystem(), new URI("http://localhost:9000/api")).init()
    val remoteApi = client.bind[Api]

    {
      // Create request context containing valid HTTP authentication used by the client transport plugin
      implicit val validAuthentication: ClientContext = client.context
        .authorization("Bearer", "valid")

      // Call the authenticated remote API function via a local proxy using implicitly given valid authentication
      println(
        remoteApi.hello("test")
      )

      // Call the remote API function dynamically using valid authentication
      println(
        client.call[String]("hello")("message" -> "test")
      )
    }

    {
      // Call the public remote API function via a local proxy without authentication
      println(
        remoteApi.public()
      )

      // Call the authenticated remote API function via a local proxy without authentication and fail
      println(Try(
        remoteApi.hello("test")
      ).failed.get)
    }

    // Close the RPC client and server
    client.close()
    server.close()
  }
}
