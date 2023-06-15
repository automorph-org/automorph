package examples.integration

import automorph.Default
import automorph.system.ZioSystem
import java.net.URI
import zio.{Task, Unsafe, ZIO}

private[examples] case object EffectSystem {
  @scala.annotation.nowarn
  def main(arguments: Array[String]): Unit = {

    // Helper function to evaluate ZIO tasks
    def run[T](effect: Task[T]): T = Unsafe.unsafe { implicit unsafe =>
      ZioSystem.defaultRuntime.unsafe.run(effect).toEither.swap.map(_.getCause).swap.toTry.get
    }

    // Create server API instance
    class ServerApi {
      def hello(some: String, n: Int): Task[String] =
        ZIO.succeed(s"Hello $some $n!")
    }
    val api = new ServerApi

    // Create ZIO effect system plugin
    val effectSystem = ZioSystem.default

    // Initialize JSON-RPC HTTP & WebSocket server listening on port 7000 for requests to '/api'
    val server = run(Default.rpcServer(effectSystem, 7000, "/api").bind(api).init())

    // Define client view of the remote API
    trait ClientApi {
      def hello(some: String, n: Int): Task[String]
    }

    // Initialize JSON-RPC HTTP client sending POST requests to 'http://localhost:7000/api'
    val client = run(
      Default.rpcClient(effectSystem, new URI("http://localhost:7000/api")).init()
    )

    // Call the remote API function statically
    val remoteApi = client.bind[ClientApi]
    println(run(
      remoteApi.hello("world", 1)
    ))

    // Close the RPC client
    run(client.close())

    // Close the RPC server
    run(server.close())
  }
}
