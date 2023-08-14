package examples.integration

import automorph.protocol.WebRpcProtocol
import automorph.{RpcClient, Default, RpcServer}
import java.net.URI
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}

private[examples] object RpcProtocol {
  @scala.annotation.nowarn
  def main(arguments: Array[String]): Unit = {

    // Helper function to evaluate Futures
    def run[T](effect: Future[T]): T = Await.result(effect, Duration.Inf)

    // Create server implementation of the remote API
    class ServerApi {

      def hello(some: String, n: Int): Future[String] =
        Future(s"Hello $some $n!")
    }
    val api = new ServerApi

    // Create a server Web-RPC protocol plugin with '/api' path prefix
    val serverRpcProtocol = WebRpcProtocol[Default.Node, Default.Codec, Default.ServerContext](
      Default.messageCodec, "/api"
    )

    // Create HTTP & WebSocket server transport listening on port 9000 for requests to '/api'
    val serverTransport = Default.serverTransport(9000, "/api")

    // Start Web-RPC HTTP & WebSocket server
    val server = run(
      RpcServer.transport(serverTransport).rpcProtocol(serverRpcProtocol).bind(api).init()
    )

    // Define a remote API
    trait Api {
      def hello(some: String, n: Int): Future[String]
    }

    // Create a client Web-RPC protocol plugin with '/api' path prefix
    val clientRpcProtocol = WebRpcProtocol[Default.Node, Default.Codec, Default.ClientContext](
      Default.messageCodec, "/api"
    )

    // Create HTTP & WebSocket client transport sending POST requests to 'http://localhost:9000/api'
    val clientTransport = Default.clientTransport(new URI("http://localhost:9000/api"))

    // Setup Web-RPC HTTP & WebSocket client
    val client = run(
      RpcClient.transport(clientTransport).rpcProtocol(clientRpcProtocol).init()
    )

    // Call the remote API function
    val remoteApi = client.bind[Api]
    println(run(
      remoteApi.hello("world", 1)
    ))

    // Close the RPC client
    run(client.close())

    // Close the RPC server
    run(server.close())
  }
}
