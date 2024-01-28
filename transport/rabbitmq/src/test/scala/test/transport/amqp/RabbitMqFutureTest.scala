package test.transport.amqp

import automorph.spi.{ClientTransport, ServerTransport}
import automorph.system.FutureSystem
import automorph.transport.amqp.client.RabbitMqClient
import automorph.transport.amqp.server.RabbitMqServer
import automorph.transport.local.client.LocalClient
import org.scalacheck.Arbitrary
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import test.base.Mutex
import test.core.ClientServerTest
import test.transport.local.LocalServer
import java.net.URI

class RabbitMqFutureTest extends ClientServerTest with Mutex {

  type Effect[T] = Future[T]
  type Context = RabbitMqServer.Context

  override lazy val system: FutureSystem = FutureSystem()
  /**
   * AMQP broker URL in 'amqp://{host}:{port}' format.
   */
  private lazy val amqpBrokerUrl = Option(System.getenv("AMQP_BROKER_URL")).map(new URI(_))
  private lazy val serverTransport = LocalServer[Future, Context](system)

  override def run[T](effect: Effect[T]): T =
    await(effect)

  override def arbitraryContext: Arbitrary[Context] =
    AmqpContextGenerator.arbitrary

  override def clientTransport(fixtureId: String): ClientTransport[Effect, Context] =
    amqpBrokerUrl.map { url =>
      RabbitMqClient[Effect](system, url, fixtureId.replaceAll(" ", "_"))
    }.getOrElse(
      LocalClient(system, arbitraryContext.arbitrary.sample.get, serverTransport.handler)
        .asInstanceOf[ClientTransport[Effect, Context]]
    )

  override def serverTransport(fixtureId: String): ServerTransport[Effect, Context] =
    amqpBrokerUrl.map { url =>
      RabbitMqServer[Effect](system, url, Seq(fixtureId.replaceAll(" ", "_")))
    }.getOrElse(
      serverTransport.asInstanceOf[ServerTransport[Effect, Context]]
    )
}
