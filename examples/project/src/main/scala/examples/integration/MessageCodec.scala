package examples.integration

import automorph.codec.messagepack.{UpickleMessagePackCodec, UpickleMessagePackCustom}
import automorph.{RpcClient, Default, RpcServer}
import java.net.URI
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}

// Introduce custom data types
private[examples] final case class Record(values: List[String])

private[examples] case object MessageCodec {

  @scala.annotation.nowarn
  def main(arguments: Array[String]): Unit = {

    // Helper function to evaluate Futures
    def run[T](effect: Future[T]): T = Await.result(effect, Duration.Inf)

    // Create uPickle message codec for JSON format
    val messageCodec = UpickleMessagePackCodec[UpickleMessagePackCustom]()

    // Provide custom data type serialization and deserialization logic
    import messageCodec.custom.*
    implicit def recordRw: messageCodec.custom.ReadWriter[Record] = messageCodec.custom.macroRW

    // Create server API instance
    class ServerApi {

      def hello(some: String, n: Int): Future[Record] =
        Future(Record(List("Hello", some, n.toString)))
    }
    val api = new ServerApi

    // Create a server RPC protocol plugin
    val serverRpcProtocol = Default.rpcProtocol[UpickleMessagePackCodec.Node, messageCodec.type, Default.ServerContext](
      messageCodec
    )

    // Create HTTP & WebSocket server transport listening on port 7000 for requests to '/api'
    val serverTransport = Default.serverTransport(Default.effectSystemAsync, 7000, "/api")

    // Initialize JSON-RPC HTTP & WebSocket server
    val server = run(
      RpcServer.transport(serverTransport).rpcProtocol(serverRpcProtocol).bind(api).init()
    )

    // Define client view of the remote API
    trait ClientApi {
      def hello(some: String, n: Int): Future[Record]
    }

    // Create a client RPC protocol plugin
    val clientRpcProtocol = Default.rpcProtocol[UpickleMessagePackCodec.Node, messageCodec.type, Default.ClientContext](
      messageCodec
    )

    // Create HTTP client transport sending POST requests to 'http://localhost:7000/api'
    val clientTransport = Default.clientTransport(Default.effectSystemAsync, new URI("http://localhost:7000/api"))

    // Initialize JSON-RPC HTTP client
    val client = run(
      RpcClient.transport(clientTransport).rpcProtocol(clientRpcProtocol).init()
    )

    // Call the remote API function
    val remoteApi = client.bind[ClientApi]
    println(run(
      remoteApi.hello("world", 1)
    ))

    // Close the RPC client
    run(client.close())

    // Close the RPC server
    run(server.close())
  }
}
