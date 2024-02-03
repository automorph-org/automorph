package examples.special

import automorph.transport.http.HttpContext
import automorph.transport.local.client.LocalClient
import automorph.{Default, RpcClient}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}

private[examples] object LocalCall {

  @scala.annotation.nowarn
  def main(arguments: Array[String]): Unit = {

    // Define a remote API
    trait Api {
      def hello(some: String, n: Int): Future[String]
    }

    // Create server implementation of the remote API
    val service = new Api {
      def hello(some: String, n: Int): Future[String] =
        Future(s"Hello $some $n!")
    }

    // Create passive JSON-RPC HTTP & WebSocket server on port 9000 for POST requests to '/api'
    val server = Default.rpcServer(9000, "/api").bind(service)

    // Create default value for request metadata of the type defined by the RPC server
    val defaultRequestContext: Default.ServerContext = HttpContext()

    // Create local client transport which passes requests directly to RPC server request handler
    val clientTransport = LocalClient(Default.effectSystem, defaultRequestContext, server.handler)

    Await.ready(for {
      // Initialize local JSON-RPC client
      client <- RpcClient.transport(clientTransport).rpcProtocol(Default.rpcProtocol).init()
      remoteApi = client.bind[Api]

      // Call the remote API function using the local client
      result <- remoteApi.hello("world", 1)
      _ = println(result)

      // Close the RPC client
      _ <- client.close()
    } yield (), Duration.Inf)
  }
}
