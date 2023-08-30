package test.transport.amqp

import automorph.spi.{ClientTransport, ServerTransport}
import automorph.system.FutureSystem
import automorph.transport.amqp.client.RabbitMqClient
import automorph.transport.amqp.server.RabbitMqServer
import automorph.transport.local.client.LocalClient
import io.arivera.oss.embedded.rabbitmq.apache.commons.lang3.SystemUtils
import io.arivera.oss.embedded.rabbitmq.{EmbeddedRabbitMq, EmbeddedRabbitMqConfig}
import java.net.URI
import java.nio.file.{Files, Paths}
import scala.jdk.CollectionConverters.IteratorHasAsScala
import org.scalacheck.Arbitrary
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.sys.process.Process
import scala.util.Try
import test.base.Mutex
import test.core.ClientServerTest
import test.transport.local.LocalServer

class RabbitMqFutureTest extends ClientServerTest with Mutex {

  type Effect[T] = Future[T]
  type Context = RabbitMqServer.Context

  private lazy val setupTimeout = 60000
  private lazy val erlangAvailable = Try(Process("erl -eval 'halt()' -noshell").! == 0).getOrElse(false)
  private lazy val embeddedBroker = createBroker()
  private lazy val serverTransport = LocalServer[Future, Context](system)

  override lazy val system: FutureSystem = FutureSystem()

  override def run[T](effect: Effect[T]): T =
    await(effect)

  override def arbitraryContext: Arbitrary[Context] =
    AmqpContextGenerator.arbitrary

  override def clientTransport(fixtureId: Int): ClientTransport[Effect, Context] =
    embeddedBroker.map { case (_, config) =>
      RabbitMqClient[Effect](url(config), fixtureId.toString, system)
    }.getOrElse(
      LocalClient(system, arbitraryContext.arbitrary.sample.get, serverTransport.handler)
        .asInstanceOf[ClientTransport[Effect, Context]]
    )

  override def serverTransport(fixtureId: Int): ServerTransport[Effect, Context] =
    embeddedBroker.map { case (_, config) =>
      RabbitMqServer[Effect](system, url(config), Seq(fixtureId.toString))
    }.getOrElse(
      serverTransport.asInstanceOf[ServerTransport[Effect, Context]]
    )

  override def integration: Boolean =
    true

  override def afterAll(): Unit =
    try {
      super.afterAll()
      embeddedBroker.foreach { case (broker, config) =>
        broker.stop()
        val brokerDirectory = config.getExtractionFolder.toPath.resolve(config.getVersion.getExtractionFolder)
        Files.walk(brokerDirectory).iterator().asScala.toSeq.reverse.foreach(_.toFile.delete())
        Try(Files.delete(brokerDirectory))
      }
    } finally {
      unlock()
    }

  private def createBroker(): Option[(EmbeddedRabbitMq, EmbeddedRabbitMqConfig)] =
    Option.when(erlangAvailable) {
      lock()
      val config = new EmbeddedRabbitMqConfig.Builder().randomPort()
        .extractionFolder(Paths.get(SystemUtils.JAVA_IO_TMPDIR, getClass.getSimpleName).toFile)
        .rabbitMqServerInitializationTimeoutInMillis(setupTimeout.toLong).build()
      val broker = new EmbeddedRabbitMq(config)
      broker.start()
      broker -> config
    }

  private def url(config: EmbeddedRabbitMqConfig): URI =
    new URI(s"amqp://localhost:${config.getRabbitMqPort}")
}
