//> using dep org.automorph::automorph-default:@PROJECT_VERSION@
//> using dep ch.qos.logback:logback-classic:@LOGGER_VERSION@
package examples.errorhandling

import automorph.Default
import automorph.transport.http.HttpContext
import java.net.URI
import java.sql.SQLException
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}

private[examples] object HttpStatusCode {

  @scala.annotation.nowarn
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

    Await.result(
      for {
        // Initialize custom JSON-RPC HTTP & WebSocket server listening on port 9000 for requests to '/api'
        server <- Default.rpcServer(9000, "/api", mapException = mapException).bind(service).init()

        // Initialize JSON-RPC HTTP client for sending POST requests to 'http://localhost:9000/api'
        client <- Default.rpcClient(new URI("http://localhost:9000/api")).init()
        remoteApi = client.bind[Api]

        // Call the remote API function via a local proxy and fail with InvalidRequestException
        error <- remoteApi.hello(1).failed
        _ = println(error)

        // Close the RPC client and server
        _ <- client.close()
        _ <- server.close()
      } yield (),
      Duration.Inf,
    )
  }
}
