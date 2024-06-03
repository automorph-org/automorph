// Serve a remote API over JSON-RPC from a selected standalone server transport layer.
//> using scala @SCALA_VERSION@
//> using dep org.automorph::automorph-default:@AUTOMORPH_VERSION@
//> using dep org.automorph::automorph-vertx:@AUTOMORPH_VERSION@
//> using dep ch.qos.logback:logback-classic:@LOGBACK_VERSION@
package examples.transport

import automorph.{Default, RpcServer}
import automorph.transport.server.VertxServer
import java.net.URI
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}

private[examples] object ServerTransport {

  @scala.annotation.nowarn
  def main(arguments: Array[String]): Unit = {

    // Define a remote API
    trait Api {
      def hello(n: Int): Future[String]
    }

    // Create server implementation of the remote API
    val service = new Api {
      override def hello(n: Int): Future[String] =
        Future(s"Hello world $n")
    }

    // Create Vert.x HTTP & WebSocket server transport plugin listening on port 9000 for requests to '/api'
    val serverTransport = VertxServer(Default.effectSystem, 9000, "/api")

    Await.result(
      for {
        // Initialize custom JSON-RPC HTTP & WebSocket server
        server <- RpcServer.transport(serverTransport).rpcProtocol(Default.rpcProtocol).service(service).init()

        // Initialize JSON-RPC HTTP client for sending POST requests to 'http://localhost:9000/api'
        client <- Default.rpcClient(new URI("http://localhost:9000/api")).init()
        remoteApi = client.proxy[Api]

        // Call the remote API function via a local proxy
        result <- remoteApi.hello(1)
        _ = println(result)

        // Close the RPC client and server
        _ <- client.close()
        _ <- server.close()
      } yield (),
      Duration.Inf,
    )
  }
}
