package examples.transport

import automorph.{Default, RpcEndpoint}
import automorph.transport.http.endpoint.UndertowHttpEndpoint
import io.undertow.{Handlers, Undertow}
import java.net.URI
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}

private[examples] case object EndpointTransport {
  @scala.annotation.nowarn
  def main(arguments: Array[String]): Unit = {

    // Helper function to evaluate Futures
    def run[T](effect: Future[T]): T = Await.result(effect, Duration.Inf)

    // Define a remote API
    trait Api {
      def hello(some: String, n: Int): Future[String]
    }

    // Create server implementation of the remote API
    val api = new Api {
      def hello(some: String, n: Int): Future[String] =
        Future(s"Hello $some $n!")
    }

    // Create Undertow JSON-RPC endpoint transport
    val endpointTransport = UndertowHttpEndpoint(Default.effectSystem)

    // Setup JSON-RPC endpoint
    val endpoint = RpcEndpoint.transport(endpointTransport).rpcProtocol(Default.rpcProtocol).bind(api)

    // Start Undertow HTTP server listening on port 9000 for requests to '/api'
    val server = Undertow.builder()
      .addHttpListener(9000, "0.0.0.0")
      .setHandler(Handlers.path().addPrefixPath("/api", endpoint.adapter))
      .build()
    server.start()

    // Initialize JSON-RPC HTTP client for sending POST requests to 'http://localhost:9000/api'
    val client = run(
      Default.rpcClient(new URI("http://localhost:9000/api")).init()
    )

    // Call the remote API function via proxy
    val remoteApi = client.bind[Api]
    println(run(
      remoteApi.hello("world", 1)
    ))

    // Close the RPC client
    run(client.close())

    // Close the RPC server
    server.stop()
  }
}
