package automorph.transport.amqp

import automorph.log.{LogProperties, Logging}
import automorph.transport.amqp.client.RabbitMqClient.Context
import automorph.util.Extensions.TryOps
import com.rabbitmq.client.AMQP.BasicProperties
import com.rabbitmq.client.{AMQP, Address, BuiltinExchangeType, Channel, Connection, ConnectionFactory, DefaultConsumer}
import java.io.IOException
import java.net.{InetAddress, URI}
import java.util.Date
import scala.collection.immutable.ListMap
import scala.jdk.CollectionConverters.{MapHasAsJava, MapHasAsScala}
import scala.util.{Try, Using}

/** Common RabbitMQ functionality. */
object RabbitMq extends Logging {

  /** Message properties. */
  final case class Message(properties: BasicProperties)

  object Message {

    /** Implicit default context value. */
    implicit val defaultContext: AmqpContext[Message] = AmqpContext()
  }

  private[automorph] final case class Session(connection: Connection, consumer: ThreadLocal[DefaultConsumer])

  /** Default direct AMQP message exchange name. */
  private[automorph] val defaultDirectExchange: String = ""

  /** Routing key property. */
  private[automorph] val routingKeyProperty = "Routing Key"

  /** Protocol name. */
  private[automorph] val protocol = "AMQP"

  /**
   * Initialize AMQP broker connection.
   *
   * @param url
   *   AMQP broker URL (amqp[s]://[username:password@]host[:port][/virtual_host])
   * @param addresses
   *   broker hostnames and ports for reconnection attempts
   * @param connectionFactory
   *   connection factory
   * @param name
   *   connection name
   * @return
   *   AMQP broker connection
   */
  private[automorph] def connect(
    url: URI,
    addresses: Seq[Address],
    name: String,
    connectionFactory: ConnectionFactory
  ): Connection = {
    val urlText = url.toString
    connectionFactory.setUri(url)
    logger.debug(s"Connecting to $protocol broker: $urlText")
    Try(if (addresses.nonEmpty) { connectionFactory.newConnection(addresses.toArray, name) }
    else { connectionFactory.newConnection(name) }).map { connection =>
      logger.info(s"Connected to $protocol broker: $urlText")
      connection
    }.onError(logger.error(s"Failed to connect to $protocol broker: $urlText", _)).get
  }

  /**
   * Declare specified direct non-durable AMQP exchange.
   *
   * @param exchange
   *   direct non-durable AMQP message exchange name
   * @param connection
   *   AMQP broker connection
   * @return
   *   nothing
   */
  private[automorph] def declareExchange(exchange: String, connection: Connection): Unit =
    Option.when(exchange != defaultDirectExchange) {
      Using(connection.createChannel()) { channel =>
        channel.exchangeDeclare(exchange, BuiltinExchangeType.DIRECT, false)
        ()
      }.get
    }.getOrElse {}

  /**
   * Close AMQP broker connection.
   *
   * @param connection
   *   AMQP broker connection
   */
  private[automorph] def close(connection: Connection): Unit =
    connection.close(AMQP.CONNECTION_FORCED, "Terminated")

  /**
   * Returns application identifier combining the local host name with specified application name.
   *
   * @param applicationName
   *   application name
   * @return
   *   application identifier
   */
  private[automorph] def applicationId(applicationName: String): String =
    s"${InetAddress.getLocalHost.getHostName}/$applicationName"

  /**
   * Creates thread-local AMQP message consumer for specified connection,
   *
   * @param connection
   *   AMQP broker connection
   * @param createConsumer
   *   AMQP message consumer creation function
   * @tparam T
   *   AMQP message consumer type
   * @return
   *   thread-local AMQP message consumer
   */
  private[automorph] def threadLocalConsumer[T <: DefaultConsumer](
    connection: Connection,
    createConsumer: Channel => T
  ): ThreadLocal[T] =
    ThreadLocal.withInitial { () =>
      val channel = connection.createChannel()
      createConsumer(Option(channel).getOrElse(throw new IOException("No AMQP connection channel available")))
    }

  /**
   * Create AMQP properties from message context.
   *
   * @param messageContext
   *   message context
   * @param contentType
   *   MIME content type
   * @param defaultReplyTo
   *   address to reply to
   * @param defaultRequestId
   *   request identifier
   * @param defaultAppId
   *   application identifier
   * @param useDefaultRequestId
   *   if true, always use specified request identifier as correlation identifier
   * @return
   *   AMQP properties
   */
  private[automorph] def amqpProperties(
    messageContext: Option[Context],
    contentType: String,
    defaultReplyTo: String,
    defaultRequestId: String,
    defaultAppId: String,
    useDefaultRequestId: Boolean,
  ): BasicProperties = {
    val context = messageContext.getOrElse(AmqpContext())
    val transportProperties = context.message.map(_.properties).getOrElse(new BasicProperties())
    new BasicProperties().builder().contentType(contentType)
      .replyTo(context.replyTo.orElse(Option(transportProperties.getReplyTo)).getOrElse(defaultReplyTo))
      .correlationId(Option.when(useDefaultRequestId)(defaultRequestId).getOrElse {
        context.correlationId.orElse(Option(transportProperties.getCorrelationId)).getOrElse(defaultRequestId)
      }).contentEncoding(context.contentEncoding.orElse(Option(transportProperties.getContentEncoding)).orNull)
      .appId(context.appId.orElse(Option(transportProperties.getAppId)).getOrElse(defaultAppId))
      .headers((context.headers ++ Option(transportProperties.getHeaders).map(_.asScala).getOrElse(Map.empty)).asJava)
      .deliveryMode(
        context.deliveryMode.map(Integer.valueOf).orElse(Option(transportProperties.getDeliveryMode)).orNull
      ).priority(context.priority.map(Integer.valueOf).orElse(Option(transportProperties.getPriority)).orNull)
      .expiration(context.expiration.orElse(Option(transportProperties.getExpiration)).orNull)
      .messageId(context.messageId.orElse(Option(transportProperties.getMessageId)).orNull)
      .timestamp(context.timestamp.map(Date.from).orElse(Option(transportProperties.getTimestamp)).orNull)
      .`type`(context.`type`.orElse(Option(transportProperties.getType)).orNull)
      .userId(context.userId.orElse(Option(transportProperties.getUserId)).orNull).build
  }

  /**
   * Create message context from AMQP properties.
   *
   * @param properties
   *   message properties
   * @return
   *   message context
   */
  private[automorph] def messageContext(properties: BasicProperties): AmqpContext[Message] =
    AmqpContext(
      contentType = Option(properties.getContentType),
      contentEncoding = Option(properties.getContentEncoding),
      headers = Option(properties.getHeaders).map(headers => Map.from(headers.asScala)).getOrElse(Map.empty),
      deliveryMode = Option(properties.getDeliveryMode.toInt),
      priority = Option(properties.getPriority.toInt),
      correlationId = Option(properties.getCorrelationId),
      replyTo = Option(properties.getReplyTo),
      expiration = Option(properties.getExpiration),
      messageId = Option(properties.getMessageId),
      timestamp = Option(properties.getTimestamp).map(_.toInstant),
      `type` = Option(properties.getType),
      userId = Option(properties.getUserId),
      appId = Option(properties.getAppId),
      message = Some(Message(properties)),
    )

  /**
   * Extract message properties from message metadata.
   *
   * @param requestId
   *   request correlation identifier
   * @param routingKey
   *   routing key
   * @param url
   *   AMQP broker URL
   * @param consumerTag
   *   consumer tag
   * @return
   *   message properties
   */
  private[automorph] def messageProperties(
    requestId: Option[String],
    routingKey: String,
    url: String,
    consumerTag: Option[String],
  ): Map[String, String] =
    ListMap() ++ requestId.map(LogProperties.requestId -> _) ++ ListMap(
      routingKeyProperty -> routingKey,
      "URL" -> url
    ) ++ consumerTag.map("Consumer Tag" -> _)
}
