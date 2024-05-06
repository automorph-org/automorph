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
  receiveRequest: Request => RequestData[Context],
  sendResponse: (ResponseData[Context], Channel, Option[Throwable] => Unit) => Response,
  protocol: Protocol,
  effectSystem: EffectSystem[Effect],
  mapException: Throwable => Int,
  requestHandler: RequestHandler[Effect, Context],
  logger: Logger,
  logResponse: Boolean = true,
) {
  private val log = MessageLog(logger, Protocol.Http.name)
  private val statusOk = 200
  private val statusInternalServerError = 500
  implicit private val system: EffectSystem[Effect] = effectSystem

  def processRequest(request: Request, channel: Channel): Effect[Response] = {
    // Receive the request
    val requestData = receiveRpcRequest(request)
    Try {
      // Process the request
      requestHandler.processRequest(requestData.body, requestData.context, requestData.id).either.map(_.fold(
        error => sendErrorResponse(error, channel, requestData),
        result => {
          // Send the response
          val responseBody = result.map(_.responseBody).getOrElse(Array.emptyByteArray)
          val resultContext = result.flatMap(_.context)
          val status = result.flatMap(_.exception).map(mapException).orElse(
            resultContext.flatMap(_.statusCode)
          ).getOrElse(statusOk)
          sendRpcResponse(responseBody, requestHandler.mediaType, status, resultContext, channel, requestData)
        },
      ))
    }.recover { error =>
      system.evaluate(sendErrorResponse(error, channel, requestData))
    }.get
  }

  def processReceiveError(error: Throwable, requestData: RequestData[Context], channel: Channel): Response = {
    log.failedReceiveRequest(error, requestData.properties, requestData.protocol.name)
    val responseBody = error.description.toByteArray
    sendRpcResponse(responseBody, contentTypeText, statusInternalServerError, None, channel, requestData)
  }

  private def receiveRpcRequest(request: Request): RequestData[Context] = {
    val protocolName = protocol.name
    log.receivingRequest(Map.empty, protocolName)
    Try(receiveRequest(request)).onSuccess { requestData =>
      requestData.body
      log.receivedRequest(requestData.properties, protocolName)
    }.onError(log.failedReceiveRequest(_, Map.empty, protocolName)).get
  }

  private def sendRpcResponse(
    responseBody: Array[Byte],
    contentType: String,
    statusCode: Int,
    context: Option[Context],
    channel: Channel,
    requestData: RequestData[Context],
  ): Response = {
    lazy val responseProperties = requestData.properties ++ (requestData.protocol match {
      case Protocol.Http => Some(LogProperties.status -> statusCode.toString)
      case _ => None
    })
    val protocolName = requestData.protocol.name
    log.sendingResponse(responseProperties, protocolName)
    val responseData = ResponseData(responseBody, context, statusCode, contentType, requestData.client, requestData.id)
    val responseResult = Try(sendResponse(responseData, channel, logResponseResult(responseProperties, protocolName)))
    if (logResponse) {
      responseResult
        .onSuccess(_ => log.sentResponse(responseProperties, protocolName))
        .onError(log.failedSendResponse(_, responseProperties, protocolName))
        .get
    } else {
      responseResult.get
    }
  }

  private def sendErrorResponse(error: Throwable, channel: Channel, requestData: RequestData[Context]): Response = {
    log.failedProcessRequest(error, requestData.properties, requestData.protocol.name)
    val responseBody = error.description.toByteArray
    sendRpcResponse(responseBody, contentTypeText, statusInternalServerError, None, channel, requestData)
  }

  private def logResponseResult(properties: Map[String, String], protocol: String)(error: Option[Throwable]): Unit =
    error.fold(log.sentResponse(properties, protocol))(log.failedSendResponse(_, properties, protocol))
}

private[automorph] object HttpRequestHandler {

  val headerXForwardedFor = "X-Forwarded-For"
  val contentTypeText = "text/plain"

  final case class RequestData[Context](
    retrieveBody: () => Array[Byte],
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
    lazy val body: Array[Byte] = retrieveBody()
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
