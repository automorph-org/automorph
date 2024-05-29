package automorph.transport

import automorph.log.{LogProperties, Logger, MessageLog}
import automorph.spi.{EffectSystem, RequestHandler}
import automorph.transport.HttpRequestHandler.{RequestData, ResponseData, contentTypeText}
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
  receiveRequest: Request => (RequestData[Context], Effect[Array[Byte]]),
  sendResponse: (ResponseData[Context], Channel) => Effect[Response],
  protocol: Protocol,
  effectSystem: EffectSystem[Effect],
  mapException: Throwable => Int,
  requestHandler: RequestHandler[Effect, Context],
  logger: Logger,
) {
  private val log = MessageLog(logger, protocol.name)
  private val statusOk = 200
  private val statusInternalServerError = 500
  implicit private val system: EffectSystem[Effect] = effectSystem

  def processRequest(request: Request, channel: Channel): Effect[Response] =
    // Receive the request
    receiveRpcRequest(request).flatMap { case (requestData, requestBody) =>
      Try {
        // Process the request
        requestHandler.processRequest(requestBody, requestData.context, requestData.id).flatFold(
          error => sendErrorResponse(error, channel, requestData),
          { result =>
            // Send the response
            val responseBody = result.map(_.responseBody).getOrElse(Array.emptyByteArray)
            val resultContext = result.flatMap(_.context)
            val statusCode = result.flatMap(_.exception).map(mapException).orElse(
              resultContext.flatMap(_.statusCode)
            ).getOrElse(statusOk)
            sendRpcResponse(responseBody, requestHandler.mediaType, statusCode, resultContext, channel, requestData)
          },
        )
      }.recover(sendErrorResponse(_, channel, requestData)).get
    }

  private def receiveRpcRequest(request: Request): Effect[(RequestData[Context], Array[Byte])] = {
    log.receivingRequest(Map.empty, protocol.name)
    Try(receiveRequest(request)).map { case (requestData, retrieveBody) =>
      retrieveBody.map { requestBody =>
        log.receivedRequest(requestData.properties, protocol.name)
        (requestData, requestBody)
      }
    }.onError(log.failedReceiveRequest(_, Map.empty, protocol.name)).get
  }

  private def sendRpcResponse(
    responseBody: Array[Byte],
    contentType: String,
    statusCode: Int,
    context: Option[Context],
    channel: Channel,
    requestData: RequestData[Context],
  ): Effect[Response] = {
    lazy val responseProperties = requestData.properties ++ (requestData.protocol match {
      case Protocol.Http => Some(LogProperties.status -> statusCode.toString)
      case _ => None
    })
    val protocol = requestData.protocol.name
    val client = requestData.client
    log.sendingResponse(responseProperties, protocol)
    val responseData = ResponseData(responseBody, context, statusCode, contentType, client, requestData.id)
    sendResponse(responseData, channel).flatFold(
      { error =>
        log.failedSendResponse(error, responseProperties, protocol)
        sendErrorResponse(error, channel, requestData)
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
    requestData: RequestData[Context],
  ): Effect[Response] = {
    log.failedProcessRequest(error, requestData.properties, requestData.protocol.name)
    val responseBody = error.description.toByteArray
    sendRpcResponse(responseBody, contentTypeText, statusInternalServerError, None, channel, requestData)
  }
}

private[automorph] object HttpRequestHandler {

  val headerXForwardedFor = "X-Forwarded-For"
  val headerPoll = "RPC-Long-Polling"
  val headerNodeId = "RPC-Node-Id"
  val headerCallId = "RPC-Call-Id"
  val contentTypeText = "text/plain"

  final case class RequestData[Context](
    context: Context,
    protocol: Protocol,
    url: String,
    client: String,
    method: Option[String] = None,
    id: String = Random.id,
  ) {
    lazy val properties: Map[String, String] = ListMap(
      LogProperties.requestId -> id,
      LogProperties.client -> client,
      LogProperties.protocol -> protocol.toString,
      LogProperties.url -> url,
    ) ++ method.map(LogProperties.method -> _)
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
