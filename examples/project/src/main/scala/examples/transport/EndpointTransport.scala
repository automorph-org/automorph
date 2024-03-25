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

    // Create HTTP server transport plugin with Undertow endpoint adapter
    val serverTransport = UndertowHttpEndpoint(Default.effectSystem)

    // Create Undertow HTTP server listening on port 9000
    val undertowServer = Undertow.builder().addHttpListener(9000, "0.0.0.0")

    val run = for {
      // Initialize JSON-RPC HTTP server using the custom transport layer
      server <- RpcServer.transport(serverTransport).rpcProtocol(Default.rpcProtocol).service(service).init()

      // Use the JSON-RPC HTTP endpoint adapter as an Undertow handler for requests to '/api'
      pathHandler = Handlers.path().addPrefixPath("/api", server.endpoint)
      apiServer = undertowServer.setHandler(pathHandler).build()

      // Start the Undertow server
      _ = apiServer.start()

      // Initialize JSON-RPC HTTP client sending POST requests to 'http://localhost:9000/api'
      client <- Default.rpcClient(new URI("http://localhost:9000/api")).init()
      remoteApi = client.proxy[Api]

      // Call the remote API function via a local proxy
      result <- remoteApi.hello(1)
      _ = println(result)

      // Close the RPC client
      _ <- client.close()

      // Close the RPC server
      _ <- server.close()

      // Stop the Undertow server
      _ = apiServer.stop()
    } yield ()
    Await.result(run, Duration.Inf)
  }
}
