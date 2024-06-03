// Serve a and call a remote API using JSON-RPC over AMQP.
//> using scala @SCALA_VERSION@
//> using dep org.automorph::automorph-default:@AUTOMORPH_VERSION@
//> using dep org.automorph::automorph-rabbitmq:@AUTOMORPH_VERSION@
//> using dep ch.qos.logback:logback-classic:@LOGBACK_VERSION@
package examples.transport

import automorph.{RpcClient, Default, RpcServer}
import automorph.transport.client.RabbitMqClient
import automorph.transport.server.RabbitMqServer
import java.net.URI
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}

private[examples] object AmqpTransport {

  @scala.annotation.nowarn
  def main(arguments: Array[String]): Unit = {

    // Define a remote API
    trait Api {
      def hello(n: Int): Future[String]
    }

    // Create server implementation of the remote API
    val service = new Api {
      override def hello(n: Int): Future[String] =
        Future(s"Hello world $n")
    }

    // Check for the AMQP broker URL configuration
    Option(System.getenv("AMQP_BROKER_URL")).map(new URI(_)).map { url =>
      // Create RabbitMQ AMQP server transport consuming requests from the 'api' queue
      val serverTransport = RabbitMqServer(Default.effectSystem, url, Seq("api"))

      // Create RabbitMQ AMQP client transport publishing requests to the 'api' queue
      val clientTransport = RabbitMqClient(Default.effectSystem, url, "api")

      val run = for {
        // Initialize custom JSON-RPC AMQP server
        server <- RpcServer.transport(serverTransport).rpcProtocol(Default.rpcProtocol).service(service).init()

        // Initialize custom JSON-RPC AMQP client
        client <- RpcClient.transport(clientTransport).rpcProtocol(Default.rpcProtocol).init()
        remoteApi = client.proxy[Api]

        // Call the remote API function via a local proxy
        result <- remoteApi.hello(1)
        _ = println(result)

        // Close the RPC client and server
        _ <- client.close()
        _ <- server.close()
      } yield ()
      Await.result(run, Duration.Inf)
    }.getOrElse {
      println("Enable AMQP example by setting AMQP_BROKER_URL environment variable to 'amqp://{host}:{port}'.")
    }
  }
}
