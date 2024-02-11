//> using dep org.automorph::automorph-default:@PROJECT_VERSION@
//> using dep org.automorph::automorph-zio:@PROJECT_VERSION@
//> using dep com.softwaremill.sttp.client3::async-http-client-backend-zio:3.3.9
//> using dep ch.qos.logback:logback-classic:@LOGGER_VERSION@
package examples.integration

import automorph.Default
import automorph.system.ZioSystem
import java.net.URI
import zio.{Console, Task, Unsafe, ZIO}

private[examples] object EffectSystem {

  @scala.annotation.nowarn
  def main(arguments: Array[String]): Unit = {

    // Define a remote API
    trait Api {
      def test(n: Int): Task[String]
    }

    // Create server implementation of the remote API
    val service = new Api {
      def test(n: Int): Task[String] =
        ZIO.succeed(s"Hello world $n")
    }

    // Create ZIO effect system plugin
    val effectSystem = ZioSystem.default

    Unsafe.unsafe { implicit unsafe =>
      ZioSystem.defaultRuntime.unsafe.run(
        for {
          // Initialize JSON-RPC HTTP & WebSocket server listening on port 9000 for requests to '/api'
          server <- Default.rpcServerCustom(effectSystem, 9000, "/api").bind(service).init()

          // Initialize JSON-RPC HTTP client for sending POST requests to 'http://localhost:9000/api'
          client <- Default.rpcClientCustom(effectSystem, new URI("http://localhost:9000/api")).init()
          remoteApi = client.bind[Api]

          // Call the remote API function via a local proxy
          result <- remoteApi.test(1)
          _ <- Console.printLine(result)

          // Close the RPC client and server
          _ <- client.close()
          _ <- server.close()
        } yield ()
      )
    }
  }
}
