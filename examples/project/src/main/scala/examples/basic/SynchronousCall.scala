// Serve a synchronous API implementation and call it remotely using JSON-RPC over HTTP(S).
//> using dep org.automorph::automorph-default:@AUTOMORPH_VERSION@
//> using dep ch.qos.logback:logback-classic:@LOGBACK_VERSION@
package examples.basic

import automorph.Default
import automorph.system.IdentitySystem
import java.net.URI

private[examples] object SynchronousCall {

  @scala.annotation.nowarn
  def main(arguments: Array[String]): Unit = {

    // Define a remote API
    trait Api {
      def hello(n: Int): String
    }

    // Create server implementation of the remote API
    val service = new Api {
      def hello(n: Int): String =
        s"Hello world $n"
    }

    // Initialize JSON-RPC HTTP & WebSocket server listening on port 9000 for POST requests to '/api'
    val server = Default.rpcServerCustom(IdentitySystem(), 9000, "/api").service(service).init()

    // Initialize JSON-RPC HTTP client for sending POST requests to 'http://localhost:9000/api'
    val client = Default.rpcClientCustom(IdentitySystem(), new URI("http://localhost:9000/api")).init()

    // Call the remote API function via a proxy instance
    val remoteApi = client.proxy[Api]
    println(
      remoteApi.hello(1)
    )

    // Call the remote API function dynamically without an API trait
    println(
      client.call[String]("hello")("n" -> 1)
    )

    // Close the RPC client and server
    client.close()
    server.close()
  }
}
