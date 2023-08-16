package examples.customization

import automorph.Default
import io.circe.{Decoder, Encoder}
import io.circe.generic.auto.*
import java.net.URI
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}

private[examples] object DataStructureSerialization {
  @scala.annotation.nowarn
  def main(arguments: Array[String]): Unit = {

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

    Await.ready(for {
      // Initialize JSON-RPC HTTP & WebSocket server listening on port 9000 for requests to '/api'
      server <- Default.rpcServer(9000, "/api").bind(api).init()

      // Initialize JSON-RPC HTTP client for sending POST requests to 'http://localhost:9000/api'
      client <- Default.rpcClient(new URI("http://localhost:9000/api")).init()
      remoteApi = client.bind[Api]

      // Call the remote API function
      result <- remoteApi.hello("world", Record("test", State.On))
      _ = println(result)

      // Close the RPC client
      _ <- client.close()

      // Close the RPC server
      _ <- server.close()
    } yield (), Duration.Inf)
  }
}
