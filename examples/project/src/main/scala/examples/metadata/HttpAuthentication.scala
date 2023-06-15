package examples.metadata

import automorph.Default
import automorph.Default.{ClientContext, ServerContext}
import java.net.URI
import scala.util.Try

private[examples] case object HttpAuthentication {
  @scala.annotation.nowarn
  def main(arguments: Array[String]): Unit = {

    // Create server API instance
    class ServerApi {

      // Accept HTTP request context provided by the server message transport plugin
      def hello(message: String)(implicit httpRequest: ServerContext): String =
        httpRequest.authorizationBearer match {
          case Some("valid") => s"Hello $message!"
          case _ => throw new IllegalAccessException("Authentication failed")
        }
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
    val remoteApi = client.bind[ClientApi]

    {
      // Create client request context containing invalid HTTP authentication
      implicit val validAuthentication: ClientContext = client.context
        .authorizationBearer("valid")

      // Call the remote API function statically using valid authentication
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

    // Close the RPC client
    client.close()

    // Close the RPC server
    server.close()
  }
}
