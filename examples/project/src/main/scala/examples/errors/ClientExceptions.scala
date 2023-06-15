package examples.errors

import automorph.{RpcClient, Default}
import java.net.URI
import java.sql.SQLException
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}
import scala.util.Try

private[examples] case object ClientExceptions {
  @scala.annotation.nowarn
  def main(arguments: Array[String]): Unit = {

    // Helper function to evaluate Futures
    def run[T](effect: Future[T]): T = Await.result(effect, Duration.Inf)

    // Create server API instance
    class ServerApi {
      def hello(some: String, n: Int): Future[String] =
        Future.failed(new IllegalArgumentException("SQL error"))
    }
    val api = new ServerApi

    // Initialize JSON-RPC HTTP & WebSocket server listening on port 7000 for requests to '/api'
    val server = run(
      Default.rpcServerAsync(7000, "/api").bind(api).init()
    )

    // Define client view of a remote API
    trait ClientApi {
      def hello(some: String, n: Int): Future[String]
    }

    // Customize remote API client RPC error to exception mapping
    val rpcProtocol = Default.rpcProtocol[Default.ClientContext].mapError((message, code) =>
      if (message.contains("SQL")) {
        new SQLException(message)
      } else {
        Default.rpcProtocol.mapError(message, code)
      }
    )

    // Create HTTP client transport sending POST requests to 'http://localhost:7000/api'
    val clientTransport = Default.clientTransport(Default.effectSystemAsync, new URI("http://localhost:7000/api"))

    // Setup custom JSON-RPC HTTP client
    val client = run(
      RpcClient.transport(clientTransport).rpcProtocol(rpcProtocol).init()
    )

    // Call the remote API function and fail with SQLException
    val remoteApi = client.bind[ClientApi]
    println(Try(run(
      remoteApi.hello("world", 1)
    )).failed.get)

    // Close the RPC client
    run(client.close())

    // Close the RPC server
    run(server.close())
  }
}
