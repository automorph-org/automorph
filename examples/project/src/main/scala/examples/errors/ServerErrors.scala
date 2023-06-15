package examples.errors

import automorph.protocol.jsonrpc.ErrorType.InvalidRequest
import automorph.protocol.jsonrpc.JsonRpcException
import automorph.{Default, RpcServer}
import java.net.URI
import java.sql.SQLException
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}
import scala.util.Try

private[examples] case object ServerErrors {
  @scala.annotation.nowarn
  def main(arguments: Array[String]): Unit = {

    // Helper function to evaluate Futures
    def run[T](effect: Future[T]): T = Await.result(effect, Duration.Inf)

    // Create server API instance
    class ServerApi {
      def hello(some: String, n: Int): Future[String] =
        if (n >= 0) {
          Future.failed(new SQLException("Invalid request"))
        } else {
          Future.failed(JsonRpcException("Application error", 1))
        }
    }
    val api = new ServerApi

    // Customize remote API server exception to RPC error mapping
    val rpcProtocol = Default.rpcProtocol[Default.ServerContext].mapException(_ match {
      case _: SQLException => InvalidRequest
      case error => Default.rpcProtocol.mapException(error)
    })

    // Create HTTP & WebSocket server transport listening on port 7000 for requests to '/api'
    val serverTransport = Default.serverTransport(Default.effectSystemAsync, 7000, "/api")

    // Initialize JSON-RPC HTTP & WebSocket server
    val server = run(
      RpcServer.transport(serverTransport).rpcProtocol(rpcProtocol).bind(api).init()
    )

    // Define client view of the remote API
    trait ClientApi {
      def hello(some: String, n: Int): Future[String]
    }

    // Initialize JSON-RPC HTTP client sending POST requests to 'http://localhost:7000/api'
    val client = run(
      Default.rpcClientAsync(new URI("http://localhost:7000/api")).init()
    )

    // Call the remote API function and fail with InvalidRequestException
    val remoteApi = client.bind[ClientApi]
    println(Try(run(
      remoteApi.hello("world", 1)
    )).failed.get)

    // Call the remote API function and fail with RuntimeException
    println(Try(run(
      remoteApi.hello("world", -1)
    )).failed.get)

    // Close the RPC client
    run(client.close())

    // Close the RPC server
    run(server.close())
  }
}
