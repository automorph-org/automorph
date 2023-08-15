package examples.integration

import automorph.Default
import automorph.system.ZioSystem
import java.net.URI
import zio.{Task, Unsafe, ZIO}

private[examples] object EffectSystem {
  @scala.annotation.nowarn
  def main(arguments: Array[String]): Unit = {

    // Define a remote API
    trait Api {
      def hello(some: String, n: Int): Task[String]
    }

    // Create server implementation of the remote API
    val api = new Api {
      def hello(some: String, n: Int): Task[String] =
        ZIO.succeed(s"Hello $some $n!")
    }

    // Create ZIO effect system plugin
    val effectSystem = ZioSystem.default

    Unsafe.unsafe { implicit unsafe =>
      ZioSystem.defaultRuntime.unsafe.run(
        for {
          // Initialize JSON-RPC HTTP & WebSocket server listening on port 9000 for requests to '/api'
          server <- Default.rpcServerCustom(effectSystem, 9000, "/api").bind(api).init()

          // Initialize JSON-RPC HTTP client for sending POST requests to 'http://localhost:9000/api'
          client <- Default.rpcClientCustom(effectSystem, new URI("http://localhost:9000/api")).init()
          remoteApi = client.bind[Api]

          // Call the remote API function
          result <- remoteApi.hello("world", 1)
          _ = println(result)

          // Close the RPC client
          _ <- client.close()

          // Close the RPC server
          _ <- server.close()
        } yield ()
      )
    }
  }
}
