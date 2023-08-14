package examples.special

import automorph.Default
import automorph.protocol.JsonRpcProtocol
import automorph.schema.{OpenApi, OpenRpc}
import java.net.URI
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}

private[examples] object ApiDiscovery {
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

    // Initialize JSON-RPC HTTP & WebSocket server with API discovery listening on port 9000 for POST requests to '/api'
    val server = run(
      Default.rpcServer(9000, "/api").discovery(true).bind(api).init()
    )

    // Initialize JSON-RPC HTTP client for sending POST requests to 'http://localhost:9000/api'
    val client = run(
      Default.rpcClient(new URI("http://localhost:9000/api")).init()
    )

    // Retrieve the remote API schema in OpenRPC format
    println(run(
      client.call[OpenRpc](JsonRpcProtocol.openRpcFunction)()
    ).methods.map(_.name))

    // Retrieve the remote API schema in OpenAPI format
    println(run(
      client.call[OpenApi](JsonRpcProtocol.openApiFunction)(),
    ).paths.get.keys.toList)

    // Close the RPC client
    run(client.close())

    // Close the RPC server
    run(server.close())
  }
}
