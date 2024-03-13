// Serve a remote API from an existing server by using a suitable endpoint transport layer.
//> using dep org.automorph::automorph-default:@AUTOMORPH_VERSION@
//> using dep ch.qos.logback:logback-classic:@LOGBACK_VERSION@
package examples.transport

import automorph.transport.server.UndertowHttpEndpoint
import automorph.{Default, RpcServer}
import io.undertow.{Handlers, Undertow}
import java.net.URI
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}

private[examples] object EndpointTransport {

  @scala.annotation.nowarn
  def main(arguments: Array[String]): Unit = {

    // Define a remote API
    trait Api {
      def hello(n: Int): Future[String]
    }

    // Create server implementation of the remote API
    val service = new Api {
      def hello(n: Int): Future[String] =
        Future(s"Hello world $n")
    }

    // Create JSON-RPC HTTP server transport with Undertow endpoint adapter
    val serverTransport = UndertowHttpEndpoint(Default.effectSystem)

    // Initialize JSON-RPC HTTP server using the custom transport layer
    val server = RpcServer.transport(serverTransport).rpcProtocol(Default.rpcProtocol).bind(service)

    // Create Undertow HTTP server listening on port 9000
    val undertowServer = Undertow.builder().addHttpListener(9000, "0.0.0.0")

    // Use the JSON-RPC HTTP endpoint adapter as an Undertow handler for requests to '/api'
    val pathHandler = Handlers.path().addPrefixPath("/api", server.endpoint)
    val apiServer = undertowServer.setHandler(pathHandler).build()

    // Start the Undertow server
    apiServer.start()

    val run = for {
      // Initialize JSON-RPC HTTP client sending POST requests to 'http://localhost:9000/api'
      client <- Default.rpcClient(new URI("http://localhost:9000/api")).init()
      remoteApi = client.bind[Api]

      // Call the remote API function via a local proxy
      result <- remoteApi.hello(1)
      _ = println(result)

      // Close the RPC client
      _ <- client.close()
    } yield ()
    Await.result(run, Duration.Inf)

    // Stop the Undertow server
    apiServer.stop()
  }
}
