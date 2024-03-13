package automorph

import automorph.handler.ApiRequestHandler
import automorph.server.meta.ServerBase
import automorph.spi.RequestHandler.Result
import automorph.spi.{ServerTransport, MessageCodec, RequestHandler, RpcProtocol}
import scala.collection.immutable.ListMap

/**
 * RPC server.
 *
 * Used to handle remote API requests as part of an existing server and invoke bound API methods to process them.
 *
 * Automatically derives remote API bindings for existing API instances.
 *
 * @constructor
 *   Creates a RPC server with specified protocol and transport plugins supporting corresponding message context type.
 * @param transport
 *   transport layer transport plugin
 * @param rpcProtocol
 *   RPC protocol plugin
 * @param handler
 *   RPC request handler
 * @param functions
 *   bound RPC functions
 * @tparam Node
 *   message node type
 * @tparam Codec
 *   message codec plugin type
 * @tparam Effect
 *   effect type
 * @tparam Context
 *   RPC message context type
 * @tparam Endpoint
 *   transport layer endpoint type
 */
final case class RpcServer[Node, Codec <: MessageCodec[Node], Effect[_], Context, Endpoint](
  transport: ServerTransport[Effect, Context, Endpoint],
  rpcProtocol: RpcProtocol[Node, Codec, Context],
  handler: RequestHandler[Effect, Context],
  functions: Seq[RpcFunction] = Seq.empty,
) extends ServerBase[Node, Codec, Effect, Context, Endpoint] {

  private val configuredTransport = transport.withHandler(handler)

  /** Transport layer integration endpoint. */
  def endpoint: Endpoint =
    configuredTransport.endpoint

  /**
   * Starts this server to process incoming requests.
   *
   * @return
   *   active RPC server
   */
  def init(): Effect[RpcServer[Node, Codec, Effect, Context, Endpoint]] =
    configuredTransport.effectSystem.map(configuredTransport.init())(_ => this)

  /**
   * Stops this server freeing the underlying resources.
   *
   * @return
   *   passive RPC server
   */
  def close(): Effect[RpcServer[Node, Codec, Effect, Context, Endpoint]] =
    configuredTransport.effectSystem.map(configuredTransport.close())(_ => this)

  /**
   * Enable or disable automatic provision of service discovery via RPC functions returning bound API schema.
   *
   * @param discovery
   *   service discovery enabled
   * @return
   *   RPC server
   */
  def discovery(discovery: Boolean): RpcServer[Node, Codec, Effect, Context, Endpoint] =
    copy(handler = handler.discovery(discovery))

  /**
   * Processes an RPC request by invoking a bound remote function based on the specified RPC request along with request
   * context and return an RPC response.
   *
   * @param requestBody
   *   request message body
   * @param context
   *   request context
   * @param id
   *   request correlation identifier included in logs associated with the request
   * @return
   *   request processing result
   */
  def processRequest(requestBody: Array[Byte], context: Context, id: String): Effect[Option[Result[Context]]] =
    handler.processRequest(requestBody, context, id)

  override def toString: String = {
    val plugins = Map[String, Any](
      "rpcProtocol" -> rpcProtocol,
      "transport" -> configuredTransport,
    ).map { case (name, plugin) =>
      s"$name = ${plugin.getClass.getName}"
    }.mkString(", ")
    s"${this.getClass.getName}($plugins)"
  }
}

object RpcServer {

  /**
   * Creates a RPC server with specified protocol and transport plugins supporting corresponding message context type.
   *
   * @param transport
   *   server transport layer plugin
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
   * @tparam Endpoint
   *   transport layer transport type
   * @return
   *   RPC server
   */
  def apply[Node, Codec <: MessageCodec[Node], Effect[_], Context, Endpoint](
    transport: ServerTransport[Effect, Context, Endpoint],
    rpcProtocol: RpcProtocol[Node, Codec, Context],
  ): RpcServer[Node, Codec, Effect, Context, Endpoint] = {
    val handler = ApiRequestHandler(transport.effectSystem, rpcProtocol, ListMap.empty)
    RpcServer(transport, rpcProtocol, handler, handler.functions)
  }

  /**
   * Creates an RPC client builder with specified effect transport plugin.
   *
   * @param transport
   *   transport protocol server plugin
   * @tparam Effect
   *   effect type
   * @tparam Context
   *   RPC message context type
   * @tparam Endpoint
   *   transport layer transport type
   * @return
   *   RPC client builder
   */
  def transport[Effect[_], Context, Endpoint](
    transport: ServerTransport[Effect, Context, Endpoint]
  ): ServerBuilder[Effect, Context, Endpoint] =
    ServerBuilder(transport)

  /**
   * RPC server builder.
   *
   * @constructor
   *   Creates a new RPC server builder.
   * @param transport
   *   server integration plugin
   * @tparam Effect
   *   effect type
   * @tparam Context
   *   RPC message context type
   * @tparam Endpoint
   *   transport layer transport type
   */
  final case class ServerBuilder[Effect[_], Context, Endpoint](
    transport: ServerTransport[Effect, Context, Endpoint]
  ) {

    /**
     * Creates a new RPC server with specified RPC protocol plugin.
     *
     * @param rpcProtocol
     *   RPC protocol plugin
     * @tparam Node
     *   message node type
     * @tparam Codec
     *   message codec plugin type
     * @return
     *   RPC server builder
     */
    def rpcProtocol[Node, Codec <: MessageCodec[Node]](
      rpcProtocol: RpcProtocol[Node, Codec, Context]
    ): RpcServer[Node, Codec, Effect, Context, Endpoint] =
      RpcServer(transport, rpcProtocol)
  }
}
