// Serve a remote API but call it locally by directly accessing the API request handler.
//> using scala @SCALA_VERSION@
//> using dep org.automorph::automorph-default:@AUTOMORPH_VERSION@
//> using dep ch.qos.logback:logback-classic:@LOGBACK_VERSION@
package examples.special

import automorph.transport.HttpContext
import automorph.transport.client.LocalClient
import automorph.{Default, RpcClient}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}

private[examples] object LocalCall {

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

    // Create passive JSON-RPC HTTP & WebSocket server on port 9000 for POST requests to '/api'
    val server = Default.rpcServer(9000, "/api").service(service)

    // Create context with default request metadata of the type defined by the RPC server
    val context: Default.ServerContext = HttpContext()

    // Create local client transport which passes requests directly to RPC server request handler
    val clientTransport = LocalClient(Default.effectSystem, context, server)

    val run = for {
      // Initialize local JSON-RPC client
      client <- RpcClient.transport(clientTransport).rpcProtocol(Default.rpcProtocol).init()
      remoteApi = client.proxy[Api]

      // Call the remote API function using the local client
      result <- remoteApi.hello(1)
      _ = println(result)

      // Close the RPC client
      _ <- client.close()
    } yield ()
    Await.result(run, Duration.Inf)
  }
}
