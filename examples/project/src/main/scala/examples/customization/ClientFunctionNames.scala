// Translate remote API method names on the client side.
//> using dep org.automorph::automorph-default:@AUTOMORPH_VERSION@
//> using dep ch.qos.logback:logback-classic:@LOGBACK_VERSION@
package examples.customization

import automorph.Default
import automorph.system.IdentitySystem
import java.net.URI

private[examples] object ClientFunctionNames {

  @scala.annotation.nowarn
  def main(arguments: Array[String]): Unit = {

    // Define client view of a remote API
    trait Api {
      def hello(n: Int): String

      def hi(n: Int): String
    }

    // Create server implementation of the remote API
    class Service {
      def hello(n: Int): String =
        s"Hello world $n"
    }
    val service = new Service

    // Initialize JSON-RPC HTTP & WebSocket server listening on port 9000 for requests to '/api'
    val server = Default.rpcServerCustom(IdentitySystem(), 9000, "/api").service(service).init()

    // Initialize JSON-RPC HTTP client sending POST requests to 'http://localhost:9000/api'
    val client = Default.rpcClientCustom(IdentitySystem(), new URI("http://localhost:9000/api")).init()

    // Customize local proxy API to RPC function name mapping
    val mapName = (name: String) =>
      name match {
        // Calling 'hi' translates to calling 'hello'
        case "hi" => "hello"

        // Other calls remain unchanged
        case other => other
      }

    // Call the remote API function via a local proxy
    val remoteApi = client.proxy[Api](mapName)
    println(
      remoteApi.hello(1)
    )

    println(
      remoteApi.hi(1)
    )

    // Close the RPC client and server
    client.close()
    server.close()
  }
}
