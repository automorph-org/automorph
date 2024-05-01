package automorph

import automorph.server.{ServerRequestHandler, ServerBinding}
import automorph.server.meta.ServerBase
import automorph.spi.RequestHandler.Result
import automorph.spi.{MessageCodec, RpcProtocol, ServerTransport}
import scala.collection.immutable.ListMap
import scala.util.Random

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
 * @param discovery
 *   enable automatic provision of service discovery via RPC functions returning bound API schema
 * @param apiBindings
 *   API method bindings
 * @tparam Node
 *   message node type
 * @tparam Codec
 *   message codec plugin type
 * @tparam Effect
 *   effect type
 * @tparam Context
 *   RPC message context type
 * @tparam Adapter
 *   transport layer adapter type
 */
final case class RpcServer[Node, Codec <: MessageCodec[Node], Effect[_], Context, Adapter](
  transport: ServerTransport[Effect, Context, Adapter],
  rpcProtocol: RpcProtocol[Node, Codec, Context],
  discovery: Boolean = false,
  apiBindings: ListMap[String, ServerBinding[Node, Effect, Context]] =
  ListMap[String, ServerBinding[Node, Effect, Context]]()
) extends ServerBase[Node, Codec, Effect, Context, Adapter] {

  private val handler = ServerRequestHandler(transport.effectSystem, rpcProtocol, discovery, apiBindings)
  private lazy val configuredTransport = transport.requestHandler(handler)
  private lazy val rpcFunctions = handler.functions

  /** Transport layer integration adapter. */
  def adapter: Adapter =
    configuredTransport.adapter

  /**
   * Starts this server to process incoming requests.
   *
   * @return
   *   active RPC server
   */
  def init(): Effect[RpcServer[Node, Codec, Effect, Context, Adapter]] =
    configuredTransport.effectSystem.map(configuredTransport.init())(_ => this)

  /**
   * Stops this server freeing the underlying resources.
   *
   * @return
   *   passive RPC server
   */
  def close(): Effect[RpcServer[Node, Codec, Effect, Context, Adapter]] =
    configuredTransport.effectSystem.map(configuredTransport.close())(_ => this)

  /**
   * Bound RPC functions.
   *
   * @return bound RPC functions
   */
  def functions: Seq[RpcFunction] =
    rpcFunctions

  /**
   * Enable or disable automatic provision of service discovery via RPC functions returning bound API schema.
   *
   * @param discovery
   *   service discovery enabled
   * @return
   *   RPC server
   */
  def discovery(discovery: Boolean): RpcServer[Node, Codec, Effect, Context, Adapter] =
    copy(discovery = discovery)

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
  def processRequest(
    requestBody: Array[Byte],
    context: Context,
    id: String = Random.nextInt(Int.MaxValue).toString,
  ): Effect[Option[Result[Context]]] =
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
