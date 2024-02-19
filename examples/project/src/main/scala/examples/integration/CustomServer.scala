// Process incoming RPC requests into remote API calls from any custom endpoint or server.
//> using dep org.automorph::automorph-default:@AUTOMORPH_VERSION@
//> using dep ch.qos.logback:logback-classic:@LOGBACK_VERSION@
package examples.integration

import automorph.transport.generic.endpoint.GenericEndpoint
import automorph.{Default, RpcEndpoint}
import java.nio.charset.StandardCharsets.UTF_8
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.Random

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

    // Create generic endpoint transport plugin with Unit as RPC request context type
    val endpointTransport = GenericEndpoint.context[Unit].effectSystem(Default.effectSystem)

    // Setup JSON-RPC endpoint and bind the API implementation to it
    val endpoint = RpcEndpoint.transport(endpointTransport).rpcProtocol(Default.rpcProtocol).bind(service)

    // Define a function for processing JSON-RPC requests via the generic RPC endpoint.
    // This function should be called from request handling logic of a custom endpoint.
    def processRpcRequest(requestBody: Array[Byte]): Future[Array[Byte]] = {
      // Supply request context of type Unit as defined by the generic endpoint transport plugin
      val requestContext: Unit = ()

      // Supply request correlation identifier which will be included in logs associated with the request
      val requestId = Random.nextInt(Int.MaxValue).toString

      // Call the remote API function by passing the request body directly to the RPC endpoint request handler
      val handlerResult = endpoint.handler.processRequest(requestBody, requestContext, requestId)

      // Extract the response body containing a JSON-RPC response from the request handler result
      handlerResult.map(_.map(_.responseBody).getOrElse(Array.emptyByteArray))
    }

    // Test the JSON-RPC request processing function
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
    val responseBody = processRpcRequest(requestBody)
    responseBody.foreach { response =>
      println(new String(response, UTF_8))
    }
  }
}
