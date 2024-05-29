package automorph.transport

import automorph.log.{LogProperties, Logging, MessageLog}
import automorph.spi.{EffectSystem, RequestHandler}
import automorph.transport.HttpRequestHandler.{RequestMetadata, ResponseData, contentTypeText}
import automorph.util.Extensions.{EffectOps, StringOps, ThrowableOps, TryOps}
import automorph.util.Random
import scala.collection.immutable.ListMap
import scala.util.Try

final private[automorph] case class HttpRequestHandler[
  Effect[_],
  Context <: HttpContext[?],
  Request,
  Response,
  Channel,
](
  receiveRequest: Request => (RequestMetadata[Context], Effect[Array[Byte]]),
  sendResponse: (ResponseData[Context], Channel) => Effect[Response],
  protocol: Protocol,
  effectSystem: EffectSystem[Effect],
  mapException: Throwable => Int,
  requestHandler: RequestHandler[Effect, Context],
) extends Logging {
  private val log = MessageLog(logger, protocol.name)
  private val statusOk = 200
  private val statusInternalServerError = 500
  implicit private val system: EffectSystem[Effect] = effectSystem

  def processRequest(request: Request, channel: Channel): Effect[Response] =
    // Receive the request
    receiveRpcRequest(request).flatMap { case (requestMetadata, requestBody) =>
      Try {
        // Process the request
        requestHandler.processRequest(requestBody, requestMetadata.context, requestMetadata.id).flatFold(
          error => sendErrorResponse(error, channel, requestMetadata),
          { result =>
            // Send the response
            val responseBody = result.map(_.responseBody).getOrElse(Array.emptyByteArray)
            val resultContext = result.flatMap(_.context)
            val statusCode = result.flatMap(_.exception).map(mapException).orElse(
              resultContext.flatMap(_.statusCode)
            ).getOrElse(statusOk)
            sendRpcResponse(responseBody, requestHandler.mediaType, statusCode, resultContext, channel, requestMetadata)
          },
        )
      }.recover(sendErrorResponse(_, channel, requestMetadata)).get
    }

  private def receiveRpcRequest(request: Request): Effect[(RequestMetadata[Context], Array[Byte])] = {
    log.receivingRequest(Map.empty, protocol.name)
    Try(receiveRequest(request)).map { case (requestMetadata, retrieveBody) =>
      retrieveBody.map { requestBody =>
        log.receivedRequest(requestMetadata.properties, protocol.name)
        (requestMetadata, requestBody)
      }
    }.onError(log.failedReceiveRequest(_, Map.empty, protocol.name)).get
  }

  private def sendRpcResponse(
    responseBody: Array[Byte],
    contentType: String,
    statusCode: Int,
    context: Option[Context],
    channel: Channel,
    requestMetadata: RequestMetadata[Context],
  ): Effect[Response] = {
    lazy val responseProperties = requestMetadata.properties ++ (requestMetadata.protocol match {
      case Protocol.Http => Some(LogProperties.status -> statusCode.toString)
      case _ => None
    })
    val protocol = requestMetadata.protocol.name
    val client = requestMetadata.client
    log.sendingResponse(responseProperties, protocol)
    val responseData = ResponseData(responseBody, context, statusCode, contentType, client, requestMetadata.id)
    sendResponse(responseData, channel).flatFold(
      { error =>
        log.failedSendResponse(error, responseProperties, protocol)
        sendErrorResponse(error, channel, requestMetadata)
      },
      { result =>
        log.sentResponse(responseProperties, protocol)
        system.successful(result)
      },
    )
  }

  private def sendErrorResponse(
    error: Throwable,
    channel: Channel,
    requestMetadata: RequestMetadata[Context],
  ): Effect[Response] = {
    log.failedProcessRequest(error, requestMetadata.properties, requestMetadata.protocol.name)
    val responseBody = error.description.toByteArray
    sendRpcResponse(responseBody, contentTypeText, statusInternalServerError, None, channel, requestMetadata)
  }
}

private[automorph] object HttpRequestHandler {

  val headerXForwardedFor = "X-Forwarded-For"
  val headerPoll = "RPC-Long-Polling"
  val headerNodeId = "RPC-Node-Id"
  val headerCallId = "RPC-Call-Id"
  val contentTypeText = "text/plain"

  /**
   * Extract client identifier from network address and HTTP headers.
   *
   * @param address
   *   network address
   * @param forwardedFor
   *   X-Forwarded-For HTTP header
   * @param nodeId
   *   RPC-Node-Id-For HTTP header
   * @return
   *   client identifier
   */
  def clientId(address: String, forwardedFor: Option[String], nodeId: Option[String]): String = {
    val finalAddress = forwardedFor.flatMap(_.split(",", 2).headOption).getOrElse {
      val lastPart = address.split("/", 2).last.replaceAll("/", "")
      lastPart.split(":").init.mkString(":")
    }
    nodeId.map(id => s"$finalAddress/$id").getOrElse(finalAddress)
  }

  final case class RequestMetadata[Context <: HttpContext[?]](
    context: Context,
    protocol: Protocol,
    url: String,
    method: Option[String] = None,
    id: String = Random.id,
  ) {
    lazy val properties: Map[String, String] = ListMap(
      LogProperties.requestId -> id,
      LogProperties.protocol -> protocol.toString,
      LogProperties.url -> url,
    ) ++ method.map(LogProperties.method -> _) ++ context.peerId.map(LogProperties.client -> _)
    lazy val client: String =
      context.peerId.getOrElse("")
  }

  final case class ResponseData[Context](
    body: Array[Byte],
    context: Option[Context],
    statusCode: Int,
    contentType: String,
    client: String,
    id: String,
  )
}
