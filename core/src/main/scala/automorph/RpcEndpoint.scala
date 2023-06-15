package automorph

import automorph.endpoint.meta.EndpointBind
import automorph.handler.ApiRequestHandler
import automorph.spi.{EndpointTransport, MessageCodec, RequestHandler, RpcProtocol}
import scala.collection.immutable.ListMap

/**
 * RPC endpoint.
 *
 * Integrates with an existing server to receive remote API requests using
 * specific transport protocol and invoke bound API methods to process them.
 *
 * Automatically derives remote API bindings for existing API instances.
 *
 * @constructor
 *   Creates a RPC endpoint with specified protocol and transport plugins supporting corresponding message context type.
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
 * @tparam Adapter
 *   transport layer adapter type
 */
final case class RpcEndpoint[Node, Codec <: MessageCodec[Node], Effect[_], Context, Adapter] (
  transport: EndpointTransport[Effect, Context, Adapter],
  rpcProtocol: RpcProtocol[Node, Codec, Context],
  handler: RequestHandler[Effect, Context],
  functions: Seq[RpcFunction] = Seq.empty,
) extends EndpointBind[Node, Codec, Effect, Context, Adapter] {

  private val configuredTransport = transport.withHandler(handler)

  /** Transport layer adapter. */
  def adapter: Adapter =
    configuredTransport.adapter

  /**
   * Enable or disable automatic provision of service discovery via RPC functions returning bound API schema.
   *
   * @param discovery service discovery enabled
   * @return RPC server
   */
  def discovery(discovery: Boolean): RpcEndpoint[Node, Codec, Effect, Context, Adapter] =
    copy(handler = handler.discovery(discovery))

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

case object RpcEndpoint {

  /**
   * RPC endpoint builder.
   *
   * @constructor
   *   Creates a new RPC endpoint builder.
   * @param transport
   *   server integration plugin
   * @tparam Effect
   *   effect type
   * @tparam Context
   *   RPC message context type
   * @tparam Adapter
   *   transport layer transport type
   */
  final case class EndpointBuilder[Effect[_], Context, Adapter](
    transport: EndpointTransport[Effect, Context, Adapter]
  ) {

    /**
     * Creates a new RPC endpoint with specified RPC protocol plugin.
     *
     * @param rpcProtocol
     *   RPC protocol plugin
     * @tparam Node
     *   message node type
     * @tparam Codec
     *   message codec plugin type
     * @return
     *   RPC endpoint builder
     */
    def rpcProtocol[Node, Codec <: MessageCodec[Node]](
      rpcProtocol: RpcProtocol[Node, Codec, Context]
    ): RpcEndpoint[Node, Codec, Effect, Context, Adapter] =
      RpcEndpoint(transport, rpcProtocol)
  }

  /**
   * Creates a RPC server with specified protocol and transport plugins supporting corresponding message context type.
   *
   * @param transport
   *   endpoint transport later transport
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
   * @tparam Adapter
   *   transport layer transport type
   * @return RPC server
   */
  def apply[Node, Codec <: MessageCodec[Node], Effect[_], Context, Adapter](
    transport: EndpointTransport[Effect, Context, Adapter],
    rpcProtocol: RpcProtocol[Node, Codec, Context],
  ): RpcEndpoint[Node, Codec, Effect, Context, Adapter] = {
    val handler = ApiRequestHandler(transport.effectSystem, rpcProtocol, ListMap.empty)
    RpcEndpoint(transport, rpcProtocol, handler, handler.functions)
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
   * @tparam Adapter
   *   transport layer transport type
   * @return
   *   RPC client builder
   */
  def transport[Effect[_], Context, Adapter](
    transport: EndpointTransport[Effect, Context, Adapter]
  ): EndpointBuilder[Effect, Context, Adapter] =
    EndpointBuilder(transport)
}
