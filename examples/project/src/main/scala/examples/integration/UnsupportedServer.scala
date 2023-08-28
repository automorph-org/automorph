package examples.integration

import automorph.transport.generic.endpoint.GenericEndpoint
import automorph.{Default, RpcEndpoint}
import java.nio.charset.StandardCharsets
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.Random

private[examples] object UnsupportedServer {
  @scala.annotation.nowarn
  def main(arguments: Array[String]): Unit = {

    // Define a remote API
    trait Api {
      def hello(some: String, n: Int): Future[String]
    }

    // Create server implementation of the remote API
    val api = new Api {
      def hello(some: String, n: Int): Future[String] =
        Future(s"Hello $some $n!")
    }

    // Create generic endpoint transport plugin with String as RPC request context type
    val endpointTransport = GenericEndpoint.context[String].effectSystem(Default.effectSystem)

    // Setup generic JSON-RPC endpoint
    val endpoint = RpcEndpoint.transport(endpointTransport).rpcProtocol(Default.rpcProtocol).bind(api)

    // Process JSON-RPC requests via the generic RPC endpoint from within server request handling logic
    {
      // Retrieve incoming request body containing a JSON-RPC request (implementation will be server specific)
      val requestBody =
        """
          |{
          |  "jsonrpc" : "2.0",
          |  "id" : "1234",
          |  "method" : "hello",
          |  "params" : {
          |    "some" : "world",
          |    "n" : 1
          |  }
          |}
          |""".getBytes(StandardCharsets.UTF_8)

      // Call the remote API function by passing the request body directly to the RPC endpoint request handler
      val handlerResult = endpoint.handler.processRequest(
        // Incoming request body
        requestBody,
        // Request context of String type as defined by the generic endpoint transport plugin
        "127.0.0.1",
        // Request correlation identifier included in logs associated with the request
        Random.nextString(8)
      )

      // Extract the response body containing a JSON-RPC response from the request handler result
      val responseBody = handlerResult.map(_.map(_.responseBody).getOrElse(Array.emptyByteArray))

      // Send the response body to the client as a response (implementation will be server specific)
      responseBody.foreach { response =>
        println(new String(response, StandardCharsets.UTF_8))
      }
    }
  }
}
