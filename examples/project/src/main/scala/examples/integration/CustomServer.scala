package examples.integration

import automorph.transport.local.client.LocalClient
import automorph.transport.local.endpoint.LocalEndpoint
import automorph.{Default, RpcClient, RpcEndpoint}
import java.nio.charset.StandardCharsets
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}
import scala.util.Random

private[examples] object CustomServer {
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

    // Create local endpoint message transport
    val endpointTransport = LocalEndpoint(Default.effectSystem, ())

    // Setup local JSON-RPC endpoint
    val endpoint = RpcEndpoint.transport(endpointTransport).rpcProtocol(Default.rpcProtocol).bind(api)

    // Create local client message transport
    val clientTransport = LocalClient(Default.effectSystem, (), endpoint.handler)

    Await.ready(for {
      // Call the remote API function by passing the request body directly to the local endpoint request handler
      result <- endpoint.handler.processRequest(
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
          |""".getBytes(StandardCharsets.UTF_8),
        (),
        Random.nextString(8)
      )

      // Extract the response body from the request handler result
      responseBody = result.map(_.responseBody).getOrElse(Array.emptyByteArray)
      _ = println(new String(responseBody, StandardCharsets.UTF_8))

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
