package automorph.transport.amqp.server

import automorph.log.{Logging, MessageLog}
import automorph.spi.{EffectSystem, RequestHandler, ServerTransport}
import automorph.transport.amqp.server.RabbitMqServer.Context
import automorph.transport.amqp.{AmqpContext, RabbitMq}
import automorph.util.Extensions.{EffectOps, StringOps, ThrowableOps, TryOps}
import com.rabbitmq.client.AMQP.BasicProperties
import com.rabbitmq.client.{Address, Channel, ConnectionFactory, DefaultConsumer, Envelope}
import java.net.URI
import scala.util.{Try, Using}
import scala.jdk.CollectionConverters.MapHasAsJava

/**
 * RabbitMQ server message transport plugin.
 *
 * Interprets AMQP request message body as an RPC request and processes it using the specified RPC request handler.
 *   - The response returned by the RPC request handler is used as outgoing AMQP response body.
 *   - AMQP request messages are consumed from the specified queues and automatically acknowledged.
 *   - AMQP response messages are published to default exchange using ''reply-to'' request property as routing key.
 *
 * @see
 *   [[https://www.rabbitmq.com/java-client.html Documentation]]
 * @see
 *   [[https://rabbitmq.github.io/rabbitmq-java-client/api/current/index.html API]]
 * @constructor
 *   Creates a RabbitMQ server message transport plugin.
 * @param effectSystem
 *   effect system plugin
 * @param url
 *   AMQP broker URL (amqp[s]://[username:password@]host[:port][/virtual_host])
 * @param queues
 *   names of non-durable exclusive queues to consume messages from
 * @param addresses
 *   broker hostnames and ports for reconnection attempts
 * @param connectionFactory
 *   AMQP broker connection factory
 * @param handler
 *   RPC request handler
 * @tparam Effect
 *   effect type
 */
final case class RabbitMqServer[Effect[_]](
  effectSystem: EffectSystem[Effect],
  url: URI,
  queues: Seq[String],
  addresses: Seq[Address] = Seq.empty,
  connectionFactory: ConnectionFactory = new ConnectionFactory,
  handler: RequestHandler[Effect, Context] = RequestHandler.dummy[Effect, Context],
) extends Logging with ServerTransport[Effect, Context] {

  private val exchange = RabbitMq.defaultDirectExchange
  private var session = Option.empty[RabbitMq.Session]
  private val serverId = RabbitMq.applicationId(getClass.getName)
  private val urlText = url.toString
  private val log = MessageLog(logger, RabbitMq.protocol)
  private implicit val system: EffectSystem[Effect] = effectSystem

  override def withHandler(handler: RequestHandler[Effect, Context]): RabbitMqServer[Effect] =
    copy(handler = handler)

  override def init(): Effect[Unit] =
    system.evaluate(this.synchronized {
      session.fold {
        val connection = RabbitMq.connect(url, addresses, serverId, connectionFactory)
        RabbitMq.declareExchange(exchange, connection)
        Using(connection.createChannel()) { channel =>
          queues.foreach { queue =>
            channel.queueDeclare(queue, false, false, false, Map.empty.asJava)
          }
        }
        val consumer = RabbitMq.threadLocalConsumer(connection, createConsumer)
        createConsumer(connection.createChannel())
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

  private def createConsumer(channel: Channel): DefaultConsumer = {
    val consumer = new DefaultConsumer(channel) {

      override def handleDelivery(
        consumerTag: String,
        envelope: Envelope,
        amqpProperties: BasicProperties,
        requestBody: Array[Byte]
      ): Unit = {
        // Log the request
        val requestId = Option(amqpProperties.getCorrelationId)
        lazy val requestProperties = RabbitMq
          .messageProperties(requestId, envelope.getRoutingKey, urlText, Option(consumerTag))
        log.receivedRequest(requestProperties)
        Option(amqpProperties.getReplyTo).map { replyTo =>
          requestId.map { actualRequestId =>
            // Process the request
            Try {
              val requestContext = RabbitMq.messageContext(amqpProperties)
              val handlerResult = handler.processRequest(requestBody.toArray[Byte], requestContext, actualRequestId)
              handlerResult.either.map(
                _.fold(
                  error => sendErrorResponse(error, replyTo, requestProperties, actualRequestId),
                  result => {
                    // Send the response
                    val responseBody = result.map(_.responseBody).getOrElse(Array.emptyByteArray)
                    sendResponse(responseBody, replyTo, result.flatMap(_.context), requestProperties, actualRequestId)
                  }
                )
              ).runAsync
            }.foldError { error =>
              sendErrorResponse(error, replyTo, requestProperties, actualRequestId)
            }
          }.getOrElse {
            logger.error(s"Missing ${log.defaultProtocol} request header: correlation-id", requestProperties)
          }
        }.getOrElse {
          logger.error(s"Missing ${log.defaultProtocol} request header: reply-to", requestProperties)
        }
      }
    }
    queues.foreach { queue =>
      consumer.getChannel.basicConsume(queue, true, consumer)
    }
    consumer
  }

  private def sendResponse(
    message: Array[Byte],
    replyTo: String,
    responseContext: Option[Context],
    requestProperties: => Map[String, String],
    requestId: String
  ): Unit = {
    // Log the response
    val actualReplyTo = responseContext.flatMap { context =>
      context.replyTo.orElse(context.message.flatMap {
        transport => Option(transport.properties.getReplyTo)
      })
    }.getOrElse(replyTo)
    lazy val responseProperties = requestProperties + (RabbitMq.routingKeyProperty -> actualReplyTo)
    log.sendingResponse(responseProperties)

    // Send the response
    Try {
      val mediaType = handler.mediaType
      val amqpProperties = RabbitMq.amqpProperties(
        responseContext,
        mediaType,
        actualReplyTo,
        requestId, serverId,
        useDefaultRequestId = true,
      )
      session.get.consumer.get.getChannel.basicPublish(
        exchange,
        actualReplyTo,
        true,
        false,
        amqpProperties,
        message,
      )
      log.sentResponse(responseProperties)
    }.onError { error =>
      log.failedSendResponse(error, responseProperties)
    }.get
  }

  private def sendErrorResponse(
    error: Throwable,
    replyTo: String,
    requestProperties: => Map[String, String],
    requestId: String
  ): Unit = {
    log.failedProcessRequest(error, requestProperties)
    val message = error.description.toByteArray
    sendResponse(message, replyTo, None, requestProperties, requestId)
  }
}

case object RabbitMqServer {

  /** Request context type. */
  type Context = AmqpContext[Message]

  /** Message properties. */
  type Message = RabbitMq.Message
}
