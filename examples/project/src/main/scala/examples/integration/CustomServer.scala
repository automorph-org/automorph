// Process incoming RPC requests into remote API calls from any custom server.
//> using dep org.automorph::automorph-default:@AUTOMORPH_VERSION@
//> using dep ch.qos.logback:logback-classic:@LOGBACK_VERSION@
package examples.integration

import automorph.transport.server.GenericEndpoint
import automorph.{Default, RpcServer}
import java.nio.charset.StandardCharsets.UTF_8
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}

private[examples] object CustomServer {

  @scala.annotation.nowarn
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

    // Create generic server transport plugin with Unit as RPC request context type
    val serverTransport = GenericEndpoint.context[Unit].effectSystem(Default.effectSystem)

    // Create example JSON-RPC request body
    val requestBody =
      """
        |{
        |  "jsonrpc" : "2.0",
        |  "id" : "1234",
        |  "method" : "hello",
        |  "params" : {
        |    "n" : 1
        |  }
        |}
        |""".getBytes(UTF_8)

    val run = for {
      // Initialize JSON-RPC server using the custom transport layer
      server <- RpcServer.transport(serverTransport).rpcProtocol(Default.rpcProtocol).service(service).init()

      // Call the remote API function by passing the request body directly to the RPC server
      rpcResult <- server.processRequest(requestBody, ())

      // Extract the response body containing a JSON-RPC response from the RPC result
      _ = rpcResult.foreach { result =>
        println(new String(result.responseBody, UTF_8))
      }

      // Close the RPC server
      _ <- server.close()
    } yield ()
    Await.result(run, Duration.Inf)
  }
}
