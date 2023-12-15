package examples.integration

import automorph.codec.messagepack.{UpickleMessagePackCodec, UpickleMessagePackCustom}
import automorph.{RpcClient, Default, RpcServer}
import java.net.URI
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}

// Introduce custom data types
private[examples] final case class Record(values: List[String])

private[examples] object MessageCodec {

  @scala.annotation.nowarn
  def main(arguments: Array[String]): Unit = {

    // Create uPickle message codec for JSON format
    val messageCodec = UpickleMessagePackCodec[UpickleMessagePackCustom]()

    // Provide custom data type serialization and deserialization logic as needed
    import messageCodec.custom.*
    implicit def recordRw: messageCodec.custom.ReadWriter[Record] = messageCodec.custom.macroRW

    // Define a remote API
    trait Api {
      def hello(some: String, n: Int): Future[Record]
    }

    // Create server implementation of the remote API
    val api = new Api {
      def hello(some: String, n: Int): Future[Record] =
        Future(Record(List("Hello", some, n.toString)))
    }

    // Create a server RPC protocol plugin
    val serverRpcProtocol = Default.rpcProtocol[
      UpickleMessagePackCodec.Node, messageCodec.type, Default.ServerContext
    ](messageCodec)

    // Create HTTP & WebSocket server transport listening on port 9000 for requests to '/api'
    val serverTransport = Default.serverTransport(9000, "/api")

    // Create a client RPC protocol plugin
    val clientRpcProtocol = Default.rpcProtocol[
      UpickleMessagePackCodec.Node, messageCodec.type, Default.ClientContext
    ](messageCodec)

    // Create HTTP client transport sending POST requests to 'http://localhost:9000/api'
    val clientTransport = Default.clientTransport(new URI("http://localhost:9000/api"))

    Await.ready(for {
      // Initialize custom JSON-RPC HTTP & WebSocket server
      server <- RpcServer.transport(serverTransport).rpcProtocol(serverRpcProtocol).bind(api).init()

      // Initialize custom JSON-RPC HTTP client
      client <- RpcClient.transport(clientTransport).rpcProtocol(clientRpcProtocol).init()
      remoteApi = client.bind[Api]

      // Call the remote API function via a local proxy
      result <- remoteApi.hello("world", 1)
      _ = println(result)

      // Close the RPC client and server
      _ <- client.close()
      _ <- server.close()
    } yield (), Duration.Inf)
  }
}
