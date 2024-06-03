// Implement, serve and call a remote API using a selected effect system.
//> using scala @SCALA_VERSION@
//> using dep org.automorph::automorph-default:@AUTOMORPH_VERSION@
//> using dep org.automorph::automorph-zio:@AUTOMORPH_VERSION@
//> using dep com.softwaremill.sttp.client3::async-http-client-backend-zio:3.3.9
//> using dep ch.qos.logback:logback-classic:@LOGBACK_VERSION@
package examples.integration

import automorph.Default
import automorph.system.ZioSystem
import java.net.URI
import zio.{Console, Runtime, Task, Unsafe, ZIO}

private[examples] object EffectSystem {

  @scala.annotation.nowarn
  def main(arguments: Array[String]): Unit = {

    // Define a remote API
    trait Api {
      def hello(n: Int): Task[String]
    }

    // Create server implementation of the remote API
    val service = new Api {
      def hello(n: Int): Task[String] =
        ZIO.succeed(s"Hello world $n")
    }

    // Create ZIO effect system plugin
    implicit val runtime: Runtime[Any] = Runtime.default
    val effectSystem = ZioSystem()

    val run = for {
      // Initialize JSON-RPC HTTP & WebSocket server listening on port 9000 for requests to '/api'
      server <- Default.rpcServerCustom(effectSystem, 9000, "/api").service(service).init()

      // Initialize JSON-RPC HTTP client sending POST requests to 'http://localhost:9000/api'
      client <- Default.rpcClientCustom(effectSystem, new URI("http://localhost:9000/api")).init()
      remoteApi = client.proxy[Api]

      // Call the remote API function via a local proxy
      result <- remoteApi.hello(1)
      _ <- Console.printLine(result)

      // Close the RPC client and server
      _ <- client.close()
      _ <- server.close()
    } yield ()
    Unsafe.unsafe { implicit unsafe =>
      effectSystem.runtime.unsafe.run(run)
    }
  }
}
