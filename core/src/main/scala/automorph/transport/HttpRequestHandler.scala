package automorph.transport

import automorph.log.{LogProperties, Logger, MessageLog}
import automorph.spi.{EffectSystem, RequestHandler}
import automorph.transport.HttpRequestHandler.{RequestData, ResponseData}
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
  sendResponse: (ResponseData[Context], Channel) => Response,
  defaultProtocol: Protocol,
  effectSystem: EffectSystem[Effect],
  mapException: Throwable => Int,
  requestHandler: RequestHandler[Effect, Context],
  logger: Logger,
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
          sendRpcResponse(responseBody, status, resultContext, channel, requestData)
        },
      ))
    }.fold(
      error => system.evaluate(sendErrorResponse(error, channel, requestData)),
      identity,
    )
  }

  private def receiveRpcRequest(request: Request): RequestData[Context] = {
    val protocol = defaultProtocol.name
    log.receivingRequest(Map.empty, protocol)
    Try(receiveRequest(request)).onSuccess { requestData =>
      requestData.body
      log.receivedRequest(requestData.properties, protocol)
    }.onError(log.failedReceiveRequest(_, Map.empty, protocol)).get
  }

  private def sendRpcResponse(
    responseBody: Array[Byte],
    statusCode: Int,
    context: Option[Context],
    channel: Channel,
    requestData: RequestData[Context],
  ): Response = {
    lazy val responseProperties = ListMap(
      LogProperties.requestId -> requestData.id,
      LogProperties.client -> requestData.client,
    ) ++ (requestData.protocol match {
      case Protocol.Http => Some(LogProperties.status -> statusCode.toString)
      case _ => None
    }).toMap
    val protocol = requestData.protocol.name
    log.sendingResponse(responseProperties, protocol)
    val contentType = requestHandler.mediaType
    val responseData = ResponseData(responseBody, context, statusCode, contentType, requestData.client, requestData.id)
    Try(sendResponse(responseData, channel)).onSuccess(_ =>
      log.sentResponse(responseProperties, protocol)
    ).onError(log.failedSendResponse(_, responseProperties, protocol)).get
  }

  private def sendErrorResponse(error: Throwable, channel: Channel, requestData: RequestData[Context]): Response = {
    log.failedProcessRequest(error, requestData.properties, requestData.protocol.name)
    val responseBody = error.description.toByteArray
    sendRpcResponse(responseBody, statusInternalServerError, None, channel, requestData)
  }
}

private[automorph] object HttpRequestHandler {

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
