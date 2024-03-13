package test.transport

import automorph.spi.{ClientTransport, ServerTransport}
import automorph.system.FutureSystem
import automorph.transport.client.RabbitMqClient
import automorph.transport.local.client.LocalClient
import automorph.transport.server.RabbitMqServer
import org.scalacheck.Arbitrary
import test.base.{Await, Mutex}
import test.core.ClientServerTest
import java.net.URI
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

final class RabbitMqFutureTest extends ClientServerTest with Await with Mutex {

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

  override def serverTransport(fixtureId: String): ServerTransport[Effect, Context, Unit] =
    amqpBrokerUrl.map { url =>
      RabbitMqServer[Effect](system, url, Seq(fixtureId.replaceAll(" ", "_")))
    }.getOrElse(
      serverTransport.asInstanceOf[ServerTransport[Effect, Context, Unit]]
    )
}
