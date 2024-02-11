//> using dep org.automorph::automorph-default:@PROJECT_VERSION@
//> using dep ch.qos.logback:logback-classic:@LOGGER_VERSION@
package examples.transport

import automorph.Default
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
      def test(n: Int): Future[String]
    }

    // Create server implementation of the remote API
    val service = new Api {
      def test(n: Int): Future[String] =
        Future(s"Hello world $n")
    }

    // Setup JSON-RPC HTTP endpoint with Undertow adapter
    val endpoint = Default.rpcEndpoint().bind(service)

    // Create Undertow HTTP server listening on port 9000
    val server = Undertow.builder().addHttpListener(9000, "0.0.0.0")

    // Use the JSON-RPC HTTP endpoint adapter as an Undertow handler for requests to '/api'
    val pathHandler = Handlers.path().addPrefixPath("/api", endpoint.adapter)
    val apiServer = server.setHandler(pathHandler).build()

    // Start the Undertow server
    apiServer.start()

    Await.ready(for {
      // Initialize JSON-RPC HTTP client for sending POST requests to 'http://localhost:9000/api'
      client <- Default.rpcClient(new URI("http://localhost:9000/api")).init()
      remoteApi = client.bind[Api]

      // Call the remote API function via a local proxy
      result <- remoteApi.test(1)
      _ = println(result)

      // Close the RPC client
      _ <- client.close()
    } yield (), Duration.Inf)

    // Stop the Undertow server
    apiServer.stop()
  }
}
