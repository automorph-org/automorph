//> using dep org.automorph::automorph-default:@PROJECT_VERSION@
//> using dep ch.qos.logback:logback-classic:@LOGGER_VERSION@
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
      def hello(who: String, n: Json): Future[Json]
    }

    // Create server implementation of the remote API
    class Service {
      def hello(who: Json, n: Int): Future[Json] =
        if (who.isString) {
          val value = who.as[String].toTry.get
          Future(Json.fromString(s"Hello $value $n!"))
        } else {
          Future(Json.fromValues(Seq(who, Json.fromInt(n))))
        }
    }
    val service = new Service

    Await.ready(for {
      // Initialize JSON-RPC HTTP client for sending POST requests to 'http://localhost:9000/api'
      server <- Default.rpcServer(9000, "/api").bind(service).init()

      // Initialize JSON-RPC HTTP client for sending POST requests to 'http://localhost:9000/api'
      client <- Default.rpcClient(new URI("http://localhost:9000/api")).init()
      remoteApi = client.bind[Api]

      // Call the remote API function a proxy instance
      result <- remoteApi.hello("test", Json.fromInt(1))
      _ = println(result)

      // Call the remote API function dynamically without an API trait
      result <- client.call[Seq[Int]]("hello")("who" -> Json.fromInt(0), "n" -> 1)
      _ = println(result)

      // Close the RPC client and server
      _ <- client.close()
      _ <- server.close()
    } yield (), Duration.Inf)
  }
}
