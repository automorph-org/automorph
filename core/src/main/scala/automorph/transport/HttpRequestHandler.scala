package automorph.transport

import automorph.log.{LogProperties, Logger, MessageLog}
import automorph.spi.{EffectSystem, RequestHandler}
import automorph.transport.HttpRequestHandler.RequestData
import automorph.util.Extensions.{EffectOps, StringOps, ThrowableOps}
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
  parseRequest: Request => RequestData[Context],
  sendResponse: (Array[Byte], Int, Option[Context], Channel) => Response,
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
    val requestData = parseRequest(request)
    log.receivingRequest(requestData.properties, requestData.protocol.name)
    requestData.body
    log.receivedRequest(requestData.properties)

    // Process the request
    Try {
      requestHandler.processRequest(requestData.body, requestData.context, requestData.id).either.map(
        _.fold(
          error => sendErrorResponse(error, channel, requestData),
          result => {
            // Send the response
            val responseBody = result.map(_.responseBody).getOrElse(Array.emptyByteArray)
            val resultContext = result.flatMap(_.context)
            val status = result.flatMap(_.exception).map(mapException).orElse(
              resultContext.flatMap(_.statusCode)
            ).getOrElse(statusOk)
            sendResponse(responseBody, status, resultContext, channel, requestData)
          },
        )
      )
    }.fold(
      error => system.evaluate(sendErrorResponse(error, channel, requestData)),
      identity,
    )
  }

  private def sendResponse(
    responseBody: Array[Byte],
    status: Int,
    context: Option[Context],
    channel: Channel,
    requestData: RequestData[Context],
  ): Response = {
    lazy val responseProperties = ListMap(
      LogProperties.requestId -> requestData.id,
      LogProperties.client -> requestData.client,
    ) ++ (requestData.protocol match {
      case Protocol.Http => Some("Status" -> status.toString)
      case _ => None
    }).toMap
    log.sendingResponse(responseProperties, requestData.protocol.name)
    val response = sendResponse(responseBody, status, context, channel)
    log.sentResponse(responseProperties, requestData.protocol.name)
    response
  }

  private def sendErrorResponse(error: Throwable, channel: Channel, requestData: RequestData[Context]): Response = {
    log.failedProcessRequest(error, requestData.properties)
    val responseBody = error.description.toByteArray
    sendResponse(responseBody, statusInternalServerError, None, channel, requestData)
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
      "Protocol" -> protocol.toString,
      "URL" -> url,
    ) ++ method.map("Method" -> _)
    lazy val body: Array[Byte] = retrieveBody()
  }
}
