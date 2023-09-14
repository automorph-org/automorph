package examples.special

import automorph.Default
import io.circe.Json
import java.net.URI
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}

private[examples] object DynamicPayload {
  @scala.annotation.nowarn
  def main(arguments: Array[String]): Unit = {

    // Define client view of a remote API
    trait Api {
      def hello(some: String, n: Json): Future[Json]
    }

    // Create server implementation of the remote API
    class ApiImpl {
      def hello(some: Json, n: Int): Future[Json] =
        if (some.isString) {
          val value = some.as[String].toTry.get
          Future(Json.fromString(s"Hello $value $n!"))
        } else {
          Future(Json.fromValues(Seq(some, Json.fromInt(n))))
        }
    }
    val api = new ApiImpl

    Await.ready(for {
      // Initialize JSON-RPC HTTP client for sending POST requests to 'http://localhost:9000/api'
      server <- Default.rpcServer(9000, "/api").bind(api).init()

      // Initialize JSON-RPC HTTP client for sending POST requests to 'http://localhost:9000/api'
      client <- Default.rpcClient(new URI("http://localhost:9000/api")).init()
      remoteApi = client.bind[Api]

      // Call the remote API function a proxy instance
      result <- remoteApi.hello("world", Json.fromInt(1))
      _ = println(result)

      // Call the remote API function dynamically without an API trait
      result <- client.call[Seq[Int]]("hello")("some" -> Json.fromInt(0), "n" -> 1)
      _ = println(result)

      // Close the RPC client and server
      _ <- client.close()
      _ <- server.close()
    } yield (), Duration.Inf)
  }
}
