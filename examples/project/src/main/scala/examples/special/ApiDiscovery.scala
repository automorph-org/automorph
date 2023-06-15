package examples.special

import automorph.Default
import automorph.protocol.JsonRpcProtocol
import automorph.schema.{OpenApi, OpenRpc}
import java.net.URI
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}

private[examples] case object ApiDiscovery {
  @scala.annotation.nowarn
  def main(arguments: Array[String]): Unit = {

    // Helper function to evaluate Futures
    def run[T](effect: Future[T]): T = Await.result(effect, Duration.Inf)

    // Create server API instance
    class ServerApi {
      def hello(some: String, n: Int): Future[String] =
        Future(s"Hello $some $n!")
    }
    val api = new ServerApi

    // Initialize JSON-RPC HTTP & WebSocket server with API discovery listening on port 7000 for POST requests to '/api'
    val server = run(
      Default.rpcServerAsync(7000, "/api").discovery(true).bind(api).init()
    )

    // Initialize JSON-RPC HTTP client sending POST requests to 'http://localhost:7000/api'
    val client = run(
      Default.rpcClientAsync(new URI("http://localhost:7000/api")).init()
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
