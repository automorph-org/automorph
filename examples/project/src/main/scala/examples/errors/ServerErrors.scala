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

private[examples] object ServerErrors {
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
        if (n >= 0) {
          Future.failed(new SQLException("Invalid request"))
        } else {
          Future.failed(JsonRpcException("Application error", 1))
        }
    }

    // Customize remote API server exception to RPC error mapping
    val rpcProtocol = Default.rpcProtocol[Default.ServerContext].mapException(_ match {
      case _: SQLException => InvalidRequest
      case error => Default.rpcProtocol.mapException(error)
    })

    // Create HTTP & WebSocket server transport listening on port 9000 for requests to '/api'
    val serverTransport = Default.serverTransport(Default.effectSystem, 9000, "/api")

    // Initialize JSON-RPC HTTP & WebSocket server
    val server = run(
      RpcServer.transport(serverTransport).rpcProtocol(rpcProtocol).bind(api).init()
    )

    // Initialize JSON-RPC HTTP client for sending POST requests to 'http://localhost:9000/api'
    val client = run(
      Default.rpcClient(new URI("http://localhost:9000/api")).init()
    )

    // Call the remote API function and fail with InvalidRequestException
    val remoteApi = client.bind[Api]
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
