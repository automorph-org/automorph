package examples.transport

import automorph.{RpcClient, Default, RpcServer}
import automorph.transport.amqp.client.RabbitMqClient
import automorph.transport.amqp.server.RabbitMqServer
import io.arivera.oss.embedded.rabbitmq.{EmbeddedRabbitMq, EmbeddedRabbitMqConfig}
import java.net.URI
import java.nio.file.Files
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}
import scala.jdk.CollectionConverters.IteratorHasAsScala
import scala.sys.process.Process
import scala.util.Try

private[examples] case object AmqpTransport {
  @scala.annotation.nowarn
  def main(arguments: Array[String]): Unit = {
    if (Try(Process("erl -eval 'halt()' -noshell").! == 0).getOrElse(false)) {

      // Define a remote API
      trait Api {
        def hello(some: String, n: Int): Future[String]
      }

      // Create server implementation of the remote API
      val api = new Api {
        override def hello(some: String, n: Int): Future[String] =
          Future(s"Hello $some $n!")
      }

      // Start embedded RabbitMQ broker
      val brokerConfig = new EmbeddedRabbitMqConfig.Builder().port(9000)
        .rabbitMqServerInitializationTimeoutInMillis(30000).build()
      val broker = new EmbeddedRabbitMq(brokerConfig)
      broker.start()

      // Create RabbitMQ AMQP server transport consuming requests from the 'api' queue
      val serverTransport = RabbitMqServer(Default.effectSystem, new URI("amqp://localhost:9000"), Seq("api"))

      // Create RabbitMQ AMQP client message transport publishing requests to the 'api' queue
      val clientTransport = RabbitMqClient(new URI("amqp://localhost:9000"), "api", Default.effectSystem)

      Await.ready(for {
        // Initialize custom JSON-RPC AMQP server
        server <- RpcServer.transport(serverTransport).rpcProtocol(Default.rpcProtocol).bind(api).init()

        // Initialize custom JSON-RPC AMQP client
        client <- RpcClient.transport(clientTransport).rpcProtocol(Default.rpcProtocol).init()
        remoteApi = client.bind[Api]

        // Call the remote API function
        result <- remoteApi.hello("world", 1)
        _ = println(result)

        // Close the RPC client
        _ <- client.close()

        // Close the RPC server
        _ <- server.close()
      } yield (), Duration.Inf)

      // Stop embedded RabbitMQ broker
      broker.stop()
      val brokerDirectory = brokerConfig.getExtractionFolder.toPath.resolve(brokerConfig.getVersion.getExtractionFolder)
      Files.walk(brokerDirectory).iterator().asScala.toSeq.reverse.foreach(_.toFile.delete())
    } else {
      println("Missing Erlang installation")
    }
  }
}
