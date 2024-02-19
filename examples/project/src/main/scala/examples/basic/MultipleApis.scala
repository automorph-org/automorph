// Serve multiple API implementations and call them remotely using JSON-RPC over HTTP(S).
//> using dep org.automorph::automorph-default:@AUTOMORPH_VERSION@
//> using dep ch.qos.logback:logback-classic:@LOGBACK_VERSION@
package examples.basic

import automorph.Default
import java.net.URI
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}

private[examples] object MultipleApis {

  @scala.annotation.nowarn
  def main(arguments: Array[String]): Unit = {

    // Define a remote API
    trait Api1 {
      def hello(n: Int): Future[String]
    }

    // Define another remote API
    trait Api2 {
      def hi(): Future[String]
    }

    // Create server implementation of the first remote API
    val service1 = new Api1 {
      def hello(n: Int): Future[String] =
        Future(s"Hello world $n")
    }

    // Create server implementation of the second remote API
    val service2 = new Api2 {
      def hi(): Future[String] =
        Future("Hola!")
    }

    Await.result(
      for {
        // Initialize JSON-RPC HTTP & WebSocket server listening on port 9000 for POST requests to '/api'
        server <- Default.rpcServer(9000, "/api").bind(service1).bind(service2).init()

        // Initialize JSON-RPC HTTP client for sending POST requests to 'http://localhost:9000/api'
        client <- Default.rpcClient(new URI("http://localhost:9000/api")).init()
        remoteApi1 = client.bind[Api1]
        remoteApi2 = client.bind[Api2]

        // Call the first remote API function
        result <- remoteApi1.hello(1)
        _ = println(result)

        // Call the second remote API function
        result <- remoteApi2.hi()
        _ = println(result)

        // Close the RPC client and server
        _ <- client.close()
        _ <- server.close()
      } yield (),
      Duration.Inf,
    )
  }
}
