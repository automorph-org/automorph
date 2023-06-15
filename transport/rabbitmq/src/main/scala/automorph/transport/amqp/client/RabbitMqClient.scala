package automorph.transport.amqp.client

import automorph.log.{Logging, MessageLog}
import automorph.spi.AsyncEffectSystem.Completable
import automorph.spi.{AsyncEffectSystem, ClientTransport, EffectSystem}
import automorph.transport.amqp.client.RabbitMqClient.{Context, Response}
import automorph.transport.amqp.{AmqpContext, RabbitMq}
import automorph.util.Extensions.{EffectOps, TryOps}
import com.rabbitmq.client.AMQP.BasicProperties
import com.rabbitmq.client.{Address, Channel, ConnectionFactory, DefaultConsumer, Envelope}
import java.net.URI
import scala.collection.concurrent.TrieMap
import scala.util.Try

/**
 * RabbitMQ client message transport plugin.
 *
 * Uses the supplied RPC request as AMQP request message body and returns AMQP response message body as a result.
 * - AMQP request messages are published to the specified exchange using ``direct reply-to``mechanism.
 * - AMQP response messages are consumed using ``direct reply-to``mechanism and automatically acknowledged.
 *
 * @see
 *   [[https://www.rabbitmq.com/java-client.html Documentation]]
 * @see
 *   [[https://rabbitmq.github.io/rabbitmq-java-client/api/current/index.html API]]
 * @constructor
 *   Creates a RabbitMQ client message transport plugin.
 * @param url
 *   AMQP broker URL (amqp[s]://[username:password@]host[:port][/virtual_host])
 * @param routingKey
 *   AMQP routing key (typically a queue name)
 * @param effectSystem
 *   effect system plugin
 * @param exchange
 *   direct non-durable AMQP message exchange name
 * @param addresses
 *   broker hostnames and ports for reconnection attempts
 * @param connectionFactory
 *   AMQP broker connection factory
 * @tparam Effect
 *   effect type
 */
final case class RabbitMqClient[Effect[_]](
  url: URI,
  routingKey: String,
  effectSystem: AsyncEffectSystem[Effect],
  exchange: String = RabbitMq.defaultDirectExchange,
  addresses: Seq[Address] = Seq.empty,
  connectionFactory: ConnectionFactory = new ConnectionFactory,
) extends Logging with ClientTransport[Effect, Context] {
  private var session = Option.empty[RabbitMq.Session]
  private val directReplyToQueue = "amq.rabbitmq.reply-to"
  private val clientId = RabbitMq.applicationId(getClass.getName)
  private val urlText = url.toString
  private val responseHandlers = TrieMap[String, Completable[Effect, Response]]()
  private val log = MessageLog(logger, RabbitMq.protocol)
  private implicit val system: EffectSystem[Effect] = effectSystem

  override def call(
    requestBody: Array[Byte],
    requestContext: Context,
    requestId: String,
    mediaType: String,
  ): Effect[Response] =
    effectSystem.completable[Response].flatMap { response =>
      send(requestBody, requestId, mediaType, requestContext, Some(response)).flatMap(_ => response.effect)
    }

  override def tell(
    requestBody: Array[Byte],
    requestContext: Context,
    requestId: String,
    mediaType: String,
  ): Effect[Unit] =
    send(requestBody, requestId, mediaType, requestContext, None)

  override def context: Context =
    RabbitMq.Message.defaultContext

  override def init(): Effect[Unit] =
    system.evaluate(this.synchronized {
      session.fold {
        val connection = RabbitMq.connect(url, addresses, clientId, connectionFactory)
        RabbitMq.declareExchange(exchange, connection)
        val consumer = RabbitMq.threadLocalConsumer(connection, createConsumer)
        session = Some(RabbitMq.Session(connection, consumer))
      }(_ => throw new IllegalStateException(s"${getClass.getSimpleName} already initialized"))
    })

  override def close(): Effect[Unit] =
    effectSystem.evaluate(this.synchronized {
      session.fold(
        throw new IllegalStateException(s"${getClass.getSimpleName} already closed")
      ) { activeSession =>
        RabbitMq.close(activeSession.connection)
        session = None
      }
    })

  private def send(
    requestBody: Array[Byte],
    defaultRequestId: String,
    mediaType: String,
    requestContext: Context,
    response: Option[Completable[Effect, Response]],
  ): Effect[Unit] = {
    // Log the request
    val amqpProperties = RabbitMq.amqpProperties(
      Some(requestContext),
      mediaType,
      directReplyToQueue,
      defaultRequestId,
      clientId,
      useDefaultRequestId = false,
    )
    val requestId = amqpProperties.getCorrelationId
    lazy val requestProperties = RabbitMq.messageProperties(Some(requestId), routingKey, urlText, None)
    log.sendingRequest(requestProperties)

    // Register deferred response effect if available
    response.foreach(responseHandlers.put(requestId, _))

    // Send the request
    effectSystem.evaluate {
      Try {
        session.get.consumer.get.getChannel.basicPublish(
          exchange,
          routingKey,
          true,
          false,
          amqpProperties,
          requestBody
        )
        log.sentRequest(requestProperties)
      }.onError { error =>
        log.failedSendRequest(error, requestProperties)
      }.get
    }
  }

  private def createConsumer(channel: Channel): DefaultConsumer = {
    val consumer = new DefaultConsumer(channel) {

      override def handleDelivery(
        consumerTag: String,
        envelope: Envelope,
        properties: BasicProperties,
        responseBody: Array[Byte],
      ): Unit = {
        // Log the response
        lazy val responseProperties = RabbitMq
          .messageProperties(Option(properties.getCorrelationId), routingKey, urlText, None)
        log.receivedResponse(responseProperties)

        // Complete the registered deferred response effect
        val responseContext = RabbitMq.messageContext(properties)
        responseHandlers.get(properties.getCorrelationId).foreach { response =>
          response.succeed(responseBody.toArray[Byte] -> responseContext).runAsync
        }
      }
    }
    consumer.getChannel.basicConsume(directReplyToQueue, true, consumer)
    consumer
  }
}

case object RabbitMqClient {

  /** Request context type. */
  type Context = AmqpContext[Message]

  /** Message properties. */
  type Message = RabbitMq.Message

  private type Response = (Array[Byte], Context)
}
