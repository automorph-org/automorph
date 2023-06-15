package examples.errors

import automorph.Default
import automorph.transport.http.HttpContext
import java.net.URI
import java.sql.SQLException
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}
import scala.util.Try

private[examples] case object HttpStatusCode {
  @scala.annotation.nowarn
  def main(arguments: Array[String]): Unit = {

    // Helper function to evaluate Futures
    def run[T](effect: Future[T]): T = Await.result(effect, Duration.Inf)

    // Create server API instance
    class ServerApi {
      def hello(some: String, n: Int): Future[String] =
        Future.failed(new SQLException("Bad request"))
    }
    val api = new ServerApi

    // Customize remote API server exception to HTTP status code mapping
    val mapException = (error: Throwable) => error match {
      case _: SQLException => 400
      case e => HttpContext.defaultExceptionToStatusCode(e)
    }

    // Start custom JSON-RPC HTTP & WebSocket server listening on port 7000 for requests to '/api'
    val server = run(
      Default.rpcServerAsync(7000, "/api", mapException = mapException).bind(api).init()
    )

    // Define client view of the remote API
    trait ClientApi {
      def hello(some: String, n: Int): Future[String]
    }

    // Initialize JSON-RPC HTTP client sending POST requests to 'http://localhost:7000/api'
    val client = run(
      Default.rpcClientAsync(new URI("http://localhost:7000/api")).init()
    )

    // Call the remote API function and fail with InvalidRequestException
    val remoteApi = client.bind[ClientApi]
    println(Try(run(
      remoteApi.hello("world", 1)
    )).failed.get)

    // Close the RPC client
    run(client.close())

    // Close the RPC server
    run(server.close())
  }
}
