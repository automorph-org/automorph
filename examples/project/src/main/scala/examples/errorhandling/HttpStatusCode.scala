// Translate remote API call exceptions to HTTP status code on the server side.
//> using scala @SCALA_VERSION@
//> using dep org.automorph::automorph-default:@AUTOMORPH_VERSION@
//> using dep ch.qos.logback:logback-classic:@LOGBACK_VERSION@
package examples.errorhandling

import automorph.Default
import automorph.transport.HttpContext
import java.net.URI
import java.sql.SQLException
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}

private[examples] object HttpStatusCode {

  def main(arguments: Array[String]): Unit = {

    // Define a remote API
    trait Api {
      def hello(n: Int): Future[String]
    }

    // Create server implementation of the remote API
    val service = new Api {
      def hello(n: Int): Future[String] =
        Future.failed(new SQLException("Bad request"))
    }

    // Customize remote API server exception to HTTP status code mapping
    val mapException = (error: Throwable) =>
      error match {
        case _: SQLException => 400
        case e => HttpContext.toStatusCode(e)
      }

    val run = for {
      // Initialize custom JSON-RPC HTTP & WebSocket server listening on port 9000 for requests to '/api'
      server <- Default.rpcServer(9000, "/api", mapException = mapException).service(service).init()

      // Initialize JSON-RPC HTTP client sending POST requests to 'http://localhost:9000/api'
      client <- Default.rpcClient(new URI("http://localhost:9000/api")).init()
      remoteApi = client.proxy[Api]

      // Call the remote API function via a local proxy and fail with InvalidRequestException
      error <- remoteApi.hello(1).failed
      _ = println(error)

      // Close the RPC client and server
      _ <- client.close()
      _ <- server.close()
    } yield ()
    Await.result(run, Duration.Inf)

  }
}
