package examples.transport

import automorph.{Default, RpcServer}
import automorph.system.FutureSystem
import automorph.transport.http.server.VertxServer
import java.net.URI
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}

private[examples] object ServerTransport {
  @scala.annotation.nowarn
  def main(arguments: Array[String]): Unit = {

    // Define a remote API
    trait Api {
      def hello(some: String, n: Int): Future[String]
    }

    // Create server implementation of the remote API
    val api = new Api {
      override def hello(some: String, n: Int): Future[String] =
        Future(s"Hello $some $n!")
    }

    // Create Vert.x HTTP & WebSocket server transport plugin listening on port 9000 for requests to '/api'
    val serverTransport = VertxServer(FutureSystem(), 9000, "/api")

    Await.ready(for {
      // Initialize custom JSON-RPC HTTP & WebSocket server
      server <- RpcServer.transport(serverTransport).rpcProtocol(Default.rpcProtocol).bind(api).init()

      // Initialize JSON-RPC HTTP client for sending POST requests to 'http://localhost:9000/api'
      client <- Default.rpcClient(new URI("http://localhost:9000/api")).init()
      remoteApi = client.bind[Api]

      // Call the remote API function
      result <- remoteApi.hello("world", 1)
      _ = println(result)

      // Close the RPC client and server
      _ <- client.close()
      _ <- server.close()
    } yield (), Duration.Inf)
  }
}
