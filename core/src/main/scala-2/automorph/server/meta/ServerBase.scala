package automorph.server.meta

import automorph.spi.{ServerTransport, MessageCodec, RpcProtocol}
import automorph.RpcServer
import scala.language.experimental.macros
import scala.reflect.macros.blackbox

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
private[automorph] trait ServerBase[Value, Codec <: MessageCodec[Value], Effect[_], Context, Endpoint] {

  def rpcProtocol: RpcProtocol[Value, Codec, Context]

  def transport: ServerTransport[Effect, Context, Endpoint]

  /**
   * Creates a copy of this server with RPC bindings for all public methods of the specified API instance.
   *
   * The binding generation fails if any public API method has one of the following properties:
   *   - does not return the specified effect type
   *   - is overloaded
   *   - has type parameters
   *   - is inline
   *
   * Bindings API methods using the names identical to already existing bindings replaces * the existing bindings
   * with the new bindings.
   *
   * If the last parameter of bound method is of `Context` type or returns a context function accepting
   * the `Context` type the server-supplied ''request context'' is passed to the bound method or
   * the returned context function as its last argument.
   *
   * @param api
   *   API instance
   * @tparam Api
   *   API type (only member methods of this type are exposed)
   * @return
   *   RPC request server with specified API bindings
   * @throws java.lang.IllegalArgumentException
   *   if invalid public methods are found in the API type
   */
  def service[Api <: AnyRef](api: Api): RpcServer[Value, Codec, Effect, Context, Endpoint] =
    macro ServerBase.serviceMacro[Value, Codec, Effect, Context, Endpoint, Api]

  /**
   * Creates a copy of this server with RPC bindings for all public methods of the specified API instance.
   *
   * The binding generation fails if any public API method has one of the following properties:
   *   - does not return the specified effect type
   *   - is overloaded
   *   - has type parameters
   *   - is inline
   *
   * Bindings API methods using the names identical to already existing bindings replaces the existing bindings
   * with the new bindings.
   *
   * If the last parameter of bound method is of `Context` type or returns a context function accepting
   * the `Context` type the server-supplied ''request context'' is passed to the bound method or
   * the returned context function as its last argument.
   *
   * Bound API methods are exposed as RPC functions with their names transformed via the `mapName` function.
   *
   * @param api
   *   API instance
   * @param mapName
   *   maps API method name to the exposed RPC function names (empty result causes the method not to be exposed)
   * @tparam Api
   *   API type (only member methods of this type are exposed)
   * @return
   *   RPC request server with specified API bindings
   * @throws java.lang.IllegalArgumentException
   *   if invalid public methods are found in the API type
   */
  def service[Api <: AnyRef](
    api: Api, mapName: String => Iterable[String]
  ): RpcServer[Value, Codec, Effect, Context, Endpoint] =
    macro ServerBase.serviceMapNameMacro[Value, Codec, Effect, Context, Endpoint, Api]
}

object ServerBase {

  def serviceMacro[Value, Codec <: MessageCodec[Value], Effect[_], Context, Endpoint, Api <: AnyRef](c: blackbox.Context)(
    api: c.Expr[Api]
  )(implicit
    nodeType: c.WeakTypeTag[Value],
    codecType: c.WeakTypeTag[Codec],
    effectType: c.WeakTypeTag[Effect[?]],
    contextType: c.WeakTypeTag[Context],
    adapterType: c.WeakTypeTag[Endpoint],
    apiType: c.WeakTypeTag[Api],
  ): c.Expr[RpcServer[Value, Codec, Effect, Context, Endpoint]] = {
    import c.universe.Quasiquote
    Seq(nodeType, codecType, effectType, contextType, adapterType, apiType)

    val mapName = c.Expr[String => Seq[String]](q"""
      (name: String) => Seq(name)
    """)
    serviceMapNameMacro[Value, Codec, Effect, Context, Endpoint, Api](c)(api, mapName)
  }

  def serviceMapNameMacro[Value, Codec <: MessageCodec[Value], Effect[_], Context, Endpoint, Api <: AnyRef](
    c: blackbox.Context
  )(
    api: c.Expr[Api], mapName: c.Expr[String => Iterable[String]]
  )(implicit
    nodeType: c.WeakTypeTag[Value],
    codecType: c.WeakTypeTag[Codec],
    effectType: c.WeakTypeTag[Effect[?]],
    contextType: c.WeakTypeTag[Context],
    adapterType: c.WeakTypeTag[Endpoint],
    apiType: c.WeakTypeTag[Api],
  ): c.Expr[RpcServer[Value, Codec, Effect, Context, Endpoint]] = {
    import c.universe.Quasiquote
    Seq(adapterType)

    // This server needs to be assigned to a stable identifier due to macro expansion limitations
    c.Expr[RpcServer[Value, Codec, Effect, Context, Endpoint]](q"""
      import automorph.server.ServerBinding
      import automorph.server.meta.ServerBindingGenerator

      val server = ${c.prefix}
      val newApiBindings = ServerBindingGenerator.generate[$nodeType, $codecType, $effectType, $contextType, $apiType](
        server.rpcProtocol.messageCodec, $api
      ).flatMap { binding =>
        $mapName(binding.function.name).map(_ -> binding)
      }
      automorph.RpcServer(server.transport, server.rpcProtocol, server.discovery, server.apiBindings ++ newApiBindings)
    """)
  }
}
