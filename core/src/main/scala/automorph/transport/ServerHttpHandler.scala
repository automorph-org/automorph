package automorph.transport

import automorph.RpcException.InvalidRequest
import automorph.log.{LogProperties, Logging, MessageLog}
import automorph.spi.{EffectSystem, RpcHandler}
import automorph.transport.ServerHttpHandler.{HttpMetadata, contentTypeText, statusInternalServerError, statusOk}
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
 * @param rpcHandler
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
final private[automorph] case class ServerHttpHandler[
  Effect[_],
  Context <: HttpContext[?],
  Request,
  Response,
  Connection,
](
  receiveRequest: Request => (Effect[Array[Byte]], HttpMetadata[Context]),
  sendResponse: (Array[Byte], HttpMetadata[Context], Connection) => Effect[Response],
  protocol: Protocol,
  effectSystem: EffectSystem[Effect],
  mapException: Throwable => Int,
  rpcHandler: RpcHandler[Effect, Context],
) extends Logging {
  private val log = MessageLog(logger, protocol.name)
  implicit private val system: EffectSystem[Effect] = effectSystem

  /**
   * Processes an HTTP or WebSocket RPC request.
   *
   * @param request
   *   request
   * @param connection
   *   HTTP or WebSocket connection
   * @return
   *   response
   */
  def processRequest(request: Request, connection: Connection): Effect[Response] =
    // Receive the request
    retrieveRequest(request).flatMap { case (requestBody, requestMetadata) =>
      // Process the request
      handleRequest(requestBody, requestMetadata, connection)
    }

  /**
   * Processes an HTTP or WebSocket RPC request.
   *
   * @param body
   *   request body
   * @param metadata
   *   request metadata
   * @param connection
   *   HTTP or WebSocket connection
   * @return
   *   response
   */
  def handleRequest(body: Array[Byte], metadata: HttpMetadata[Context], connection: Connection): Effect[Response] =
    if (metadata.contentType == rpcHandler.mediaType) {
      // Process the request
      rpcHandler.processRequest(body, metadata.context, metadata.id).flatFold(
        error => respondError(error, connection, metadata),
        { result =>
          // Send the response
          val responseBody = result.map(_.responseBody).getOrElse(Array.emptyByteArray)
          val context = result.flatMap(_.context)
          lazy val statusCode = result.flatMap(_.exception).map(mapException).orElse(context.flatMap(_.statusCode))
          respond(responseBody, rpcHandler.mediaType, statusCode, context, metadata, connection)
        },
      )
    } else {
      system.failed(InvalidRequest(s"Invalid content type: ${metadata.contentType}"))
    }

  /**
   * Retrieves HTTP or WebSocket request body and metadata.
   *
   * @param request
   *   request
   * @return
   *   request body and metadata
   */
  def retrieveRequest(request: Request): Effect[(Array[Byte], HttpMetadata[Context])] = {
    log.receivingRequest(Map.empty, protocol.name)
    Try(receiveRequest(request)).map { case (retrieveBody, requestMetadata) =>
      retrieveBody.map { requestBody =>
        log.receivedRequest(requestMetadata.properties, protocol.name)
        requestBody -> requestMetadata
      }
    }.onError(log.failedReceiveRequest(_, Map.empty, protocol.name)).get
  }

  /**
   * Sends HTTP or WebSocket response.
   *
   * @param body
   *   response body
   * @param contentType
   *   response content type
   * @param statusCode
   *   HTTP status code
   * @param context
   *   response context
   * @param metadata
   *   response metadata
   * @param connection
   *   HTTP or WebSocket connection
   * @return
   *   response
   */
  def respond(
    body: Array[Byte],
    contentType: String,
    statusCode: => Option[Int],
    context: Option[Context],
    metadata: HttpMetadata[Context],
    connection: Connection,
  ): Effect[Response] = {
    val responseMetadata = metadata.copy(
      contentType = rpcHandler.mediaType,
      statusCode = if (protocol == Protocol.Http) statusCode.orElse(Some(statusOk)) else None,
      context = context.getOrElse(HttpContext().asInstanceOf[Context])
    )
    val protocolName = metadata.protocol.name
    log.sendingResponse(responseMetadata.properties, protocolName)
    sendResponse(body, responseMetadata, connection).flatFold(
      { error =>
        log.failedSendResponse(error, responseMetadata.properties, protocolName)
        system.failed(error)
      },
      { result =>
        log.sentResponse(responseMetadata.properties, protocolName)
        system.successful(result)
      },
    )
  }

  /**
   * Sends HTTP or WebSocket error response.
   *
   * @param error
   *   error
   *   response context
   * @param connection
   *   HTTP or WebSocket connection
   * @param metadata
   *   response metadata
   * @return
   *   response
   */
  def respondError(
    error: Throwable,
    connection: Connection,
    metadata: HttpMetadata[Context],
  ): Effect[Response] = {
    log.failedProcessRequest(error, metadata.properties, metadata.protocol.name)
    val responseBody = error.description.toByteArray
    respond(responseBody, contentTypeText, Some(statusInternalServerError), None, metadata, connection)
  }
}

private[automorph] object ServerHttpHandler {

  val headerXForwardedFor = "X-Forwarded-For"
  val valueRpcListen = "true"
  val contentTypeText = "text/plain"
  private val statusOk = 200
  private val statusInternalServerError = 500

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
  def client(address: String, forwardedFor: Option[String], nodeId: Option[String]): String = {
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

  final case class HttpMetadata[Context <: HttpContext[?]](
    context: Context,
    protocol: Protocol,
    method: Option[String] = None,
    url: Option[String],
    contentType: String,
    statusCode: Option[Int],
    id: String = Random.id,
  ) {
    lazy val properties: Map[String, String] = ListMap(
      LogProperties.requestId -> id,
      LogProperties.protocol -> protocol.toString,
    ) ++ url.map(LogProperties.url -> _)
      ++ method.map(LogProperties.method -> _)
      ++ statusCode.map(LogProperties.status -> _.toString)
      ++ context.peer.map(LogProperties.client -> _)
    lazy val statusCodeOrOk: Int = statusCode.getOrElse(statusOk)
  }

  object HttpMetadata {

    def apply[Context <: HttpContext[?]](
      context: Context,
      protocol: Protocol,
      url: String,
      method: Option[String],
    ): HttpMetadata[Context] = {
      val contentType = context.contentType.getOrElse("")
      HttpMetadata(context, protocol, method, Some(url), contentType, None)
    }
  }
}
