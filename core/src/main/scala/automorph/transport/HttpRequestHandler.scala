package automorph.transport

import automorph.log.{LogProperties, Logging, MessageLog}
import automorph.spi.{EffectSystem, RequestHandler}
import automorph.transport.HttpRequestHandler.{RequestMetadata, ResponseMetadata, contentTypeText}
import automorph.util.Extensions.{EffectOps, StringOps, ThrowableOps, TryOps}
import automorph.util.Random
import java.net.{InetSocketAddress, SocketAddress}
import scala.collection.immutable.ListMap
import scala.util.Try

/**
 * HTTP or WebSocket RPC request handler.
 *
 * @constructor
 *   Creates a HTTP or WebSocket RPC request handler.
 * @param receiveRequest
 *   function to receive a HTTP/WebSocket request
 * @param sendResponse
 *   function to send a HTTP/WebSocket response
 * @param protocol
 *   transport protocol
 * @param effectSystem
 *   effect system plugin
 * @param mapException
 *   maps an exception to a corresponding HTTP status code
 * @param requestHandler
 *   RPC request handler
 * @tparam Effect
 *   effect type
 * @tparam Context
 *   RPC message context type
 * @tparam Request
 *   HTTP/WebSocket request type
 * @tparam Response
 *   HTTP/WebSocket response type
 * @tparam Connection
 *   HTTP/WebSocket connection type
 */
final private[automorph] case class HttpRequestHandler[
  Effect[_],
  Context <: HttpContext[?],
  Request,
  Response,
  Connection,
](
  receiveRequest: Request => (RequestMetadata[Context], Effect[Array[Byte]]),
  sendResponse: (ResponseMetadata[Context], Connection) => Effect[Response],
  protocol: Protocol,
  effectSystem: EffectSystem[Effect],
  mapException: Throwable => Int,
  requestHandler: RequestHandler[Effect, Context],
) extends Logging {
  val statusOk = 200
  private val log = MessageLog(logger, protocol.name)
  private val statusInternalServerError = 500
  implicit private val system: EffectSystem[Effect] = effectSystem

  def processRequest(
    requestBody: Array[Byte],
    requestMetadata: RequestMetadata[Context],
    connection: Connection,
  ): Effect[Response] =
    // Process the request
    requestHandler.processRequest(requestBody, requestMetadata.context, requestMetadata.id).flatFold(
      error => sendErrorResponse(error, connection, requestMetadata),
      { result =>
        // Send the response
        val responseBody = result.map(_.responseBody).getOrElse(Array.emptyByteArray)
        val resultContext = result.flatMap(_.context)
        val statusCode = result.flatMap(_.exception).map(mapException).orElse(
          resultContext.flatMap(_.statusCode)
        ).getOrElse(statusOk)
        sendRpcResponse(responseBody, requestHandler.mediaType, statusCode, resultContext, connection, requestMetadata)
      },
    )

  def receiveRpcRequest(request: Request): Effect[(RequestMetadata[Context], Array[Byte])] = {
    log.receivingRequest(Map.empty, protocol.name)
    Try(receiveRequest(request)).map { case (requestMetadata, retrieveBody) =>
      retrieveBody.map { requestBody =>
        log.receivedRequest(requestMetadata.properties, protocol.name)
        (requestMetadata, requestBody)
      }
    }.onError(log.failedReceiveRequest(_, Map.empty, protocol.name)).get
  }

  def sendRpcResponse(
    responseBody: Array[Byte],
    contentType: String,
    statusCode: Int,
    context: Option[Context],
    connection: Connection,
    requestMetadata: RequestMetadata[Context],
  ): Effect[Response] = {
    lazy val responseProperties = requestMetadata.properties ++ (requestMetadata.protocol match {
      case Protocol.Http => Some(LogProperties.status -> statusCode.toString)
      case _ => None
    })
    val protocol = requestMetadata.protocol.name
    val client = requestMetadata.client
    log.sendingResponse(responseProperties, protocol)
    val responseData = ResponseMetadata(responseBody, context, statusCode, contentType, client, requestMetadata.id)
    sendResponse(responseData, connection).flatFold(
      { error =>
        log.failedSendResponse(error, responseProperties, protocol)
        system.failed(error)
      },
      { result =>
        log.sentResponse(responseProperties, protocol)
        system.successful(result)
      },
    )
  }

  private def sendErrorResponse(
    error: Throwable,
    connection: Connection,
    requestMetadata: RequestMetadata[Context],
  ): Effect[Response] = {
    log.failedProcessRequest(error, requestMetadata.properties, requestMetadata.protocol.name)
    val responseBody = error.description.toByteArray
    sendRpcResponse(responseBody, contentTypeText, statusInternalServerError, None, connection, requestMetadata)
  }
}

private[automorph] object HttpRequestHandler {

  val headerXForwardedFor = "X-Forwarded-For"
  val valueRpcListen = "true"
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

  /**
   * Extract server properties from network address.
   *
   * @param address
   *   network address
   * @return
   *   server properties
   */
  def serverProperties(address: SocketAddress): ListMap[String, String] =
    address match {
      case address: InetSocketAddress =>
        ListMap("Host" -> address.getHostString, "Port" -> address.getPort.toString)
      case _ => ListMap()
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

  final case class ResponseMetadata[Context](
    body: Array[Byte],
    context: Option[Context],
    statusCode: Int,
    contentType: String,
    client: String,
    id: String,
  )
}
