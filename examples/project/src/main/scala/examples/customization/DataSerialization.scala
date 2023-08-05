package examples.customization

import automorph.Default
import io.circe.{Decoder, Encoder}
import io.circe.generic.auto.*
import java.net.URI
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}

private[examples] object DataSerialization {
  @scala.annotation.nowarn
  def main(arguments: Array[String]): Unit = {

    // Helper function to evaluate Futures
    def run[T](effect: Future[T]): T = Await.result(effect, Duration.Inf)

    // Introduce custom data types
    sealed abstract class State

    case object State {
      case object On extends State
      case object Off extends State
    }

    final case class Record(
      value: String,
      state: State
    )

    // Provide custom data type serialization and deserialization logic
    implicit val enumEncoder: Encoder[State] = Encoder.encodeInt.contramap[State](Map(
      State.Off -> 0,
      State.On -> 1
    ))
    implicit val enumDecoder: Decoder[State] = Decoder.decodeInt.map(Map(
      0 -> State.Off,
      1 -> State.On
    ))

    // Define a remote API
    trait Api {
      def hello(some: String, record: Record): Future[Record]
    }

    // Create server implementation of the remote API
    val api = new Api {
      def hello(some: String, record: Record): Future[Record] =
        Future(record.copy(value = s"Hello $some!"))
    }

    // Initialize JSON-RPC HTTP & WebSocket server listening on port 9000 for requests to '/api'
    val server = run(
      Default.rpcServerAsync(9000, "/api").bind(api).init()
    )

    // Initialize JSON-RPC HTTP client for sending POST requests to 'http://localhost:9000/api'
    val client = run(
      Default.rpcClientAsync(new URI("http://localhost:9000/api")).init()
    )

    // Call the remote API function
    val remoteApi = client.bind[Api]
    println(run(
      remoteApi.hello("world", Record("test", State.On))
    ))

    // Close the RPC client
    run(client.close())

    // Close the RPC server
    run(server.close())
  }
}
