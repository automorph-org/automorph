// Serve an API implementation and call it remotely using JSON-RPC over HTTP(S).
//> using scala @SCALA_VERSION@
//> using dep org.automorph::automorph-default:@AUTOMORPH_VERSION@
//> using dep ch.qos.logback:logback-classic:@LOGBACK_VERSION@
package examples.web

import automorph.Default
import java.net.URI
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}
import scala.sys.process.Process
import scala.util.Try

private[examples] object JavaScriptClient {

  @scala.annotation.nowarn
  def main(arguments: Array[String]): Unit = {

    // Define a remote API
    trait Api {
      def hello(n: Int): Future[String]
    }

    // Create server implementation of the remote API
    class Service {
      def hello(n: Int): Future[String] =
        Future(s"Hello world $n")
    }
    val service = new Service

    if (Try(Process("node --version").!!).isSuccess) {
      // Configure JSON-RPC HTTP & WebSocket server to listen on port 9000 for requests to '/api'
      val inactiveServer = Default.rpcServer(9000, "/api")

      // Register the API implementation to be available as a remote service
      val apiServer = inactiveServer.service(service)

      // Configure JSON-RPC HTTP client to send POST requests to 'http://localhost:9000/api'
      val inactiveClient = Default.rpcClient(new URI("http://localhost:9000/api"))

      // Create a type-safe local proxy for the remote API from the API trait
      val remoteApi = inactiveClient.proxy[Api]

      val run = for {
        // Start the JSON-RPC server
        server <- apiServer.init()

        // Call the remote API function via the local proxy
        result <- remoteApi.hello(1)
        _ = println(result)

        // Call the remote API function dynamically using a JavaScript client
//        result <- Future(Process("node examples/project/src/main/resources/examples/JavaScriptClient.js").!!)
//        _ = println(result)

        // Stop the JSON-RPC server
        _ <- server.close()
      } yield ()
      Await.result(run, Duration.Inf)
    } else {
      val name = getClass.getSimpleName
      println(s"Enable $name example by installing NodeJs")
    }
  }
}
