package automorph.server.meta

import automorph.RpcServer
import automorph.server.ServerBinding
import automorph.spi.{MessageCodec, RpcProtocol, ServerTransport}
import scala.collection.immutable.ListMap

/**
 * Server API method bindings layer.
 *
 * @tparam Value
 *   message node type
 * @tparam Codec
 *   message codec plugin type
 * @tparam Effect
 *   effect type
 * @tparam Context
 *   RPC message context type
 * @tparam Endpoint
 *   transport layer transport type
 */
private[automorph] trait ServerBase[Value, Codec <: MessageCodec[Value], Effect[_], Context, Endpoint]:

  def transport: ServerTransport[Effect, Context, Endpoint]

  def rpcProtocol: RpcProtocol[Value, Codec, Context]

  def discovery: Boolean

  def apiBindings: ListMap[String, ServerBinding[Value, Effect, Context]]

  /**
   * Creates a copy of this server with RPC bindings for all public methods of the specified API instance.
   *
   * The binding generation fails if any public API method has one of the following properties:
   *   - does not return the specified effect type
   *   - is overloaded
   *   - has type parameters
   *   - is inline
   *
   * Bindings API methods using the names identical to already existing bindings replaces * the existing bindings with
   * the new bindings.
   *
   * If the last parameter of bound method is of `Context` type or returns a context function accepting the `Context`
   * type the server-supplied ''request context'' is passed to the bound method or the returned context function as
   * its last argument.
   *
   * @param api
   *   API instance
   * @tparam Api
   *   API type (only member methods of this type are exposed)
   * @return
   *   RPC request server with specified API bindings
   * @throws IllegalArgumentException
   *   if invalid public methods are found in the API type
   */
  inline def service[Api <: AnyRef](api: Api): RpcServer[Value, Codec, Effect, Context, Endpoint] =
    service(api, Seq(_))

  /**
   * Creates a copy of this server with RPC bindings for all public methods of the specified API instance.
   *
   * The binding generation fails if any public API method has one of the following properties:
   *   - does not return the specified effect type
   *   - is overloaded
   *   - has type parameters
   *   - is inline
   *
   * Bindings API methods using the names identical to already existing bindings replaces the existing bindings with the
   * new bindings.
   *
   * If the last parameter of bound method is of `Context` type or returns a context function accepting the `Context`
   * type the server-supplied ''request context'' is passed to the bound method or the returned context function as
   * its last argument.
   *
   * Bound API methods are exposed as RPC functions with their names transformed via the `mapName` function.
   *
   * @param api
   *   API instance
   * @param mapName
   *   maps bound API method name to the exposed RPC function names (empty result causes the method not to be exposed)
   * @tparam Api
   *   API type (only member methods of this type are exposed)
   * @return
   *   RPC request server with specified API bindings
   * @throws IllegalArgumentException
   *   if invalid public methods are found in the API type
   */
  inline def service[Api <: AnyRef](
    api: Api,
    mapName: String => Iterable[String],
  ): RpcServer[Value, Codec, Effect, Context, Endpoint] =
    val newApiBindings = ServerBindingGenerator.generate[Value, Codec, Effect, Context, Api](
      rpcProtocol.messageCodec,
      api,
    ).flatMap { binding =>
      mapName(binding.function.name).map(_ -> binding)
    }
    RpcServer(transport, rpcProtocol, discovery, apiBindings ++ newApiBindings)
