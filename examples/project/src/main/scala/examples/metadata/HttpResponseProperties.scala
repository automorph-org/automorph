//> using dep org.automorph::automorph-default:@PROJECT_VERSION@
//> using dep ch.qos.logback:logback-classic:@LOGGER_VERSION@
package examples.metadata

import automorph.Default.{ClientContext, ServerContext}
import automorph.system.IdentitySystem
import automorph.transport.http.HttpContext
import automorph.{RpcResult, Default}
import java.net.URI

private[examples] object HttpResponseProperties {

  @scala.annotation.nowarn
  def main(arguments: Array[String]): Unit = {

    // Define client view of a remote API
    trait Api {
      // Return HTTP response context provided by the client transport plugin
      def test(message: String): RpcResult[String, ClientContext]
    }

    // Create server implementation of the remote API
    class Service {

      // Return HTTP response context consumed by the server message transport plugin
      def test(message: String): RpcResult[String, ServerContext] = RpcResult(
        message,
        HttpContext().headers("X-Test" -> "value", "Cache-Control" -> "no-cache").statusCode(200)
      )
    }
    val service = new Service

    // Initialize JSON-RPC HTTP & WebSocket server listening on port 9000 for requests to '/api'
    val server = Default.rpcServerCustom(IdentitySystem(), 9000, "/api").bind(service).init()

    // Initialize JSON-RPC HTTP client for sending POST requests to 'http://localhost:9000/api'
    val client = Default.rpcClientCustom(IdentitySystem(), new URI("http://localhost:9000/api")).init()

    // Call the remote API function via a local proxy retrieving a result with HTTP response metadata
    val remoteApi = client.bind[Api]
    val static = remoteApi.test("test")
    println(static.result)
    println(static.context.header("X-Test"))

    // Call the remote API function dynamically retrieving a result with HTTP response metadata
    val dynamic = client.call[RpcResult[String, ClientContext]]("test")("message" -> "test")
    println(dynamic.result)
    println(dynamic.context.header("X-Test"))

    // Close the RPC client and server
    client.close()
    server.close()
  }
}
