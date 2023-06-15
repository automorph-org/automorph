package automorph

import automorph.RpcException.InvalidResponse
import automorph.client.meta.ClientBind
import automorph.client.RemoteTell
import automorph.log.{LogProperties, Logging}
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
 * Remote APIs can be invoked statically using transparent proxy instances automatically derived from specified API
 * traits or dynamically by supplying the required type information on invocation.
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
) extends ClientBind[Node, Codec, Effect, Context] with Logging {

  private implicit val system: EffectSystem[Effect] = transport.effectSystem

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
   *   active RPC server
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
    ).map { case (name, plugin) => s"$name = ${plugin.getClass.getName}" }.mkString(", ")
    s"${this.getClass.getName}($plugins)"
  }

  /**
   * This method must never be used and should be be considered private.
   *
   * Calls a remote API function using specified arguments.
   *
   * Optional request context is used as a last remote function argument.
   *
   * @param function
   *   remote function name
   * @param arguments
   *   named arguments
   * @param decodeResult
   *   decodes remote function result
   * @param requestContext
   *   request context
   * @tparam Result
   *   result type
   * @return
   *   result value
   */
  override def performCall[Result](
    function: String,
    arguments: Seq[(String, Node)],
    decodeResult: (Node, Context) => Result,
    requestContext: Option[Context],
  ): Effect[Result] = {
    // Create request
    val requestId = Random.id
    rpcProtocol.createRequest(
      function,
      arguments,
      responseRequired = true,
      requestContext.getOrElse(context),
      requestId,
    ).fold(
      error => system.failed(error),
      // Send request
      rpcRequest =>
        system.successful(rpcRequest).flatMap { request =>
          lazy val requestProperties = ListMap(LogProperties.requestId -> requestId) ++ rpcRequest.message.properties
          lazy val allProperties = requestProperties ++ rpcRequest.message.text.map(LogProperties.messageBody -> _)
          logger.trace(s"Sending ${rpcProtocol.name} request", allProperties)
          transport.call(request.message.body, request.context, requestId, rpcProtocol.messageCodec.mediaType).flatMap {
            case (responseBody, responseContext) =>
              // Process response
              processResponse[Result](responseBody, responseContext, requestProperties, decodeResult)
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
      responseRequired = false,
      requestContext.getOrElse(context),
      requestId,
    ).fold(
      error => system.failed(error),
      // Send request
      rpcRequest =>
        system.successful(rpcRequest).flatMap { request =>
          lazy val requestProperties = rpcRequest.message.properties + (LogProperties.requestId -> requestId)
          lazy val allProperties = requestProperties ++ rpcRequest.message.text.map(LogProperties.messageBody -> _)
          logger.trace(s"Sending ${rpcProtocol.name} request", allProperties)
          transport.tell(request.message.body, request.context, requestId, rpcProtocol.messageCodec.mediaType)
        },
    )
  }

  /**
   * Processes an remote function call response.
   *
   * @param responseBody
   *   response message body
   * @param responseContext
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
    responseBody: Array[Byte],
    responseContext: Context,
    requestProperties: => Map[String, String],
    decodeResult: (Node, Context) => R,
  ): Effect[R] =
    // Parse response
    rpcProtocol.parseResponse(responseBody, responseContext).fold(
      error => raiseError[R](error.exception, requestProperties),
      rpcResponse => {
        lazy val allProperties = requestProperties ++ rpcResponse.message.properties ++
          rpcResponse.message.text.map(LogProperties.messageBody -> _)
        logger.trace(s"Received ${rpcProtocol.name} response", allProperties)
        rpcResponse.result.fold(
          // Raise error
          error => raiseError[R](error, requestProperties),
          // Decode result
          result =>
            Try(decodeResult(result, responseContext)).fold(
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

case object RpcClient {

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
}
