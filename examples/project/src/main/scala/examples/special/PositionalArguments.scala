package examples.special

import automorph.{RpcClient, Default}
import java.net.URI
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}

private[examples] case object PositionalArguments {
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

    // Initialize JSON-RPC HTTP & WebSocket server listening on port 7000 for POST requests to '/api'
    val server = run(
      Default.rpcServerAsync(7000, "/api").bind(api).init(),
    )

    // Define client view of the remote API
    trait ClientApi {
      def hello(some: String, n: Int): Future[String]
    }

    // Configure JSON-RPC to pass arguments by position instead of by name
    val rpcProtocol = Default.rpcProtocol[Default.ClientContext].namedArguments(false)

    // Create HTTP client transport sending POST requests to 'http://localhost:7000/api'
    val clientTransport = Default.clientTransport(Default.effectSystemAsync, new URI("http://localhost:7000/api"))

    // Initialize JSON-RPC HTTP client
    val client = run(
      RpcClient.transport(clientTransport).rpcProtocol(rpcProtocol).init()
    )

    // Call the remote API function
    val remoteApi = client.bind[ClientApi]
    println(run(
      remoteApi.hello("world", 1),
    ))

    // Close the RPC client
    run(client.close())

    // Close the RPC server
    run(server.close())
  }
}
