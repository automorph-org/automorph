package automorph

import automorph.RpcException.InvalidResponse
import automorph.client.meta.ClientBase
import automorph.client.RemoteTell
import automorph.log.{LogProperties, Logging}
import automorph.spi.protocol.{Message, Request}
import automorph.spi.{ClientTransport, EffectSystem, MessageCodec, RpcProtocol}
import automorph.util.Extensions.EffectOps
import automorph.util.Random
import scala.collection.immutable.ListMap
import scala.util.Try

/**
 * RPC client.
 *
 * Used to perform type-safe remote API calls or send one-way messages.
 *
 * Remote APIs can be invoked via proxy instances automatically created from specified API trait or without an API trait
 * by supplying the required type information on invocation.
 *
 * @constructor
 *   Creates a RPC client with specified protocol and transport plugins providing corresponding message context type.
 * @param transport
 *   client transport protocol plugin
 * @param rpcProtocol
 *   RPC protocol plugin
 * @tparam Node
 *   message node type
 * @tparam Codec
 *   message codec plugin type
 * @tparam Effect
 *   effect type
 * @tparam Context
 *   RPC message context type
 */
final case class RpcClient[Node, Codec <: MessageCodec[Node], Effect[_], Context](
  transport: ClientTransport[Effect, Context],
  rpcProtocol: RpcProtocol[Node, Codec, Context],
) extends ClientBase[Node, Codec, Effect, Context] with Logging {

  implicit private val system: EffectSystem[Effect] = transport.effectSystem

  /**
   * Creates a remote API function one-way message proxy.
   *
   * Uses the remote function name and arguments to send an RPC request without waiting for a response.
   *
   * @param function
   *   remote function name
   * @return
   *   specified remote function one-way message proxy
   * @throws RpcException
   *   on RPC error
   */
  def tell(function: String): RemoteTell[Node, Codec, Effect, Context] =
    RemoteTell(function, rpcProtocol.messageCodec, performTell)

  /**
   * Creates a default request context.
   *
   * @return
   *   request context
   */
  def context: Context =
    transport.context

  /**
   * Starts this server to process incoming requests.
   *
   * @return
   *   active RPC client
   */
  def init(): Effect[RpcClient[Node, Codec, Effect, Context]] =
    system.map(transport.init())(_ => this)

  /**
   * Closes this client freeing the underlying resources.
   *
   * @return
   *   nothing
   */
  def close(): Effect[RpcClient[Node, Codec, Effect, Context]] =
    system.map(transport.close())(_ => this)

  override def toString: String = {
    val plugins = Map[String, Any](
      "rpcProtocol" -> rpcProtocol,
      "transport" -> transport,
    ).map { case (name, plugin) =>
      s"$name = ${plugin.getClass.getName}"
    }.mkString(", ")
    s"${this.getClass.getName}($plugins)"
  }

  /** This method must never be used and should be considered private. */
  override def performCall[Result](
    function: String,
    arguments: Seq[(String, Node)],
    decodeResult: (Node, Context) => Result,
    requestContext: Option[Context],
  ): Effect[Result] = {
    // Create request
    val requestId = Random.id
    rpcProtocol.createRequest(function, arguments, respond = true, requestContext.getOrElse(context), requestId).fold(
      error => system.failed(error),
      // Send request
      rpcRequest =>
        system.successful(rpcRequest).flatMap { request =>
          lazy val requestProperties = getRequestProperties(rpcRequest, requestId)
          lazy val allProperties = requestProperties ++ getMessageBody(rpcRequest.message)
          logger.trace(s"Sending ${rpcProtocol.name} request", allProperties)
          transport
            .call(request.message.body, request.context, requestId, rpcProtocol.messageCodec.mediaType)
            .flatMap { case (responseBody, responseContext) =>
              // Process response
              processResponse[Result](responseBody, responseContext, requestId, requestProperties, decodeResult)
            }
        },
    )
  }

  /**
   * Send a one-way message to a remote API function using specified arguments.
   *
   * Optional request context is used as a last remote function argument.
   *
   * @param function
   *   remote function name
   * @param arguments
   *   named arguments
   * @param requestContext
   *   request context
   * @return
   *   nothing
   */
  private def performTell(
    function: String,
    arguments: Seq[(String, Node)],
    requestContext: Option[Context],
  ): Effect[Unit] = {
    // Create request
    val requestId = Random.id
    rpcProtocol.createRequest(
      function,
      arguments,
      respond = false,
      requestContext.getOrElse(context),
      requestId,
    ).fold(
      error => system.failed(error),
      // Send request
      rpcRequest =>
        system.successful(rpcRequest).flatMap { request =>
          lazy val requestProperties = getRequestProperties(rpcRequest, requestId)
          lazy val allProperties = requestProperties ++ getMessageBody(rpcRequest.message)
          logger.trace(s"Sending ${rpcProtocol.name} request", allProperties)
          transport.tell(request.message.body, request.context, requestId, rpcProtocol.messageCodec.mediaType)
        },
    )
  }

  private def getRequestProperties(
    rpcRequest: Request[Node, rpcProtocol.Metadata, Context],
    requestId: String,
  ): Map[String, String] =
    ListMap(LogProperties.requestId -> requestId) ++ rpcRequest.message.properties

  private def getMessageBody(message: Message[?]): Option[(String, String)] =
    message.text.map(LogProperties.messageBody -> _)

  /**
   * Processes an remote function call response.
   *
   * @param body
   *   response message body
   * @param context
   *   response context
   * @param requestProperties
   *   request properties
   * @param decodeResult
   *   decodes remote function call result
   * @tparam R
   *   result type
   * @return
   *   result value
   */
  private def processResponse[R](
    body: Array[Byte],
    context: Context,
    requestId: String,
    requestProperties: => Map[String, String],
    decodeResult: (Node, Context) => R,
  ): Effect[R] =
    // Parse response
    rpcProtocol.parseResponse(body, context, requestId).fold(
      error => raiseError[R](error.exception, requestProperties),
      { rpcResponse =>
        lazy val responseProperties = rpcResponse.message.properties
        lazy val allProperties = requestProperties ++ responseProperties ++ getMessageBody(rpcResponse.message)
        logger.trace(s"Received ${rpcProtocol.name} response", allProperties)
        rpcResponse.result.fold(
          // Raise error
          error => raiseError[R](error, requestProperties),
          // Decode result
          result =>
            Try(decodeResult(result, context)).fold(
              error => raiseError[R](InvalidResponse("Malformed result", error), requestProperties),
              result => {
                logger.info(s"Performed ${rpcProtocol.name} request", requestProperties)
                system.successful(result)
              },
            ),
        )
      },
    )

  /**
   * Creates an error effect from an exception.
   *
   * @param error
   *   exception
   * @param properties
   *   message properties
   * @tparam T
   *   effectful value type
   * @return
   *   error value
   */
  private def raiseError[T](error: Throwable, properties: Map[String, String]): Effect[T] = {
    logger.error(s"Failed to perform ${rpcProtocol.name} request", error, properties)
    system.failed(error)
  }
}

object RpcClient {

  /**
   * Creates an RPC client builder with specified effect transport plugin.
   *
   * @param transport
   *   transport protocol plugin
   * @tparam Effect
   *   effect type
   * @tparam Context
   *   RPC message context type
   * @return
   *   RPC client builder
   */
  def transport[Effect[_], Context](transport: ClientTransport[Effect, Context]): ClientBuilder[Effect, Context] =
    ClientBuilder(transport)

  /**
   * RPC client builder.
   *
   * @constructor
   *   Creates a new RPC client builder.
   * @param transport
   *   transport protocol plugin
   * @tparam Effect
   *   effect type
   * @tparam Context
   *   request context type
   */
  final case class ClientBuilder[Effect[_], Context](transport: ClientTransport[Effect, Context]) {

    /**
     * Creates a new RPC client with specified RPC protocol plugin.
     *
     * @param rpcProtocol
     *   RPC protocol plugin
     * @tparam Node
     *   message node type
     * @tparam Codec
     *   message codec plugin type
     * @return
     *   RPC client builder
     */
    def rpcProtocol[Node, Codec <: MessageCodec[Node]](
      rpcProtocol: RpcProtocol[Node, Codec, Context]
    ): RpcClient[Node, Codec, Effect, Context] =
      RpcClient(transport, rpcProtocol)
  }
}
