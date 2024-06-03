// Define remote API with optional parameters and call it without supplying them.
//> using scala @SCALA_VERSION@
//> using dep org.automorph::automorph-default:@AUTOMORPH_VERSION@
//> using dep ch.qos.logback:logback-classic:@LOGBACK_VERSION@
package examples.basic

import automorph.Default
import java.net.URI
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}

private[examples] object OptionalParameters {

  def main(arguments: Array[String]): Unit = {

    // Define client view of a remote API
    trait Api {
      def hello(who: String): Future[String]
    }

    // Create server implementation of the remote API
    class Service {
      def hello(who: String, n: Option[Int]): Future[String] =
        Future(s"Hello $who ${n.getOrElse(0)}")

      def hi(who: Option[String])(n: Int): Future[String] =
        Future(s"Hi ${who.getOrElse("all")} $n")
    }
    val service = new Service

    val run = for {
      // Initialize JSON-RPC HTTP & WebSocket server listening on port 9000 for requests to '/api'
      server <- Default.rpcServer(9000, "/api").service(service).init()

      // Initialize JSON-RPC HTTP client sending POST requests to 'http://localhost:9000/api'
      client <- Default.rpcClient(new URI("http://localhost:9000/api")).init()
      remoteApi = client.proxy[Api]

      // Call the remote API function via a proxy instance
      result <- remoteApi.hello("world")
      _ = println(result)

      // Call the remote API function dynamically without an API trait
      result <- client.call[String]("hi")("n" -> 1)
      _ = println(result)

      // Close the RPC client and server
      _ <- client.close()
      _ <- server.close()
    } yield ()
    Await.result(run, Duration.Inf)

  }
}
