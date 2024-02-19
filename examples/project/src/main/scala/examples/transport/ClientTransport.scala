// Call a remote API using JSON-RPC over a selected client transport layer.
//> using dep org.automorph::automorph-default:@AUTOMORPH_VERSION@
//> using dep org.automorph::automorph-sttp:@AUTOMORPH_VERSION@
//> using dep com.softwaremill.sttp.client3::async-http-client-backend-future:@STTP_VERSION@
//> using dep ch.qos.logback:logback-classic:@LOGBACK_VERSION@
package examples.transport

import automorph.{Default, RpcClient}
import automorph.transport.client.SttpClient
import sttp.client3.asynchttpclient.future.AsyncHttpClientFutureBackend
import java.net.URI
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}

private[examples] object ClientTransport {

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

    // Create STTP client transport with asynchronous HTTP backend sending POST requests to 'http://localhost:9000/api'
    val clientTransport = SttpClient(
      Default.effectSystem,
      AsyncHttpClientFutureBackend(),
      new URI("http://localhost:9000/api"),
    )

    Await.result(
      for {
        // Initialize JSON-RPC HTTP & WebSocket server listening on port 80 for requests to '/api'
        server <- Default.rpcServer(9000, "/api").bind(service).init()

        // Initialize custom JSON-RPC HTTP client
        client <- RpcClient.transport(clientTransport).rpcProtocol(Default.rpcProtocol).init()
        remoteApi = client.bind[Api]

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
