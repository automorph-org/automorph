package automorph.endpoint.meta

import automorph.spi.{EndpointTransport, MessageCodec, RequestHandler, RpcProtocol}
import automorph.RpcEndpoint
import scala.language.experimental.macros
import scala.reflect.macros.blackbox

/**
 * Endpoint API method bindings layer.
 *
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
 */
private[automorph] trait EndpointBind[Node, Codec <: MessageCodec[Node], Effect[_], Context, Adapter] {

  def rpcProtocol: RpcProtocol[Node, Codec, Context]

  def transport: EndpointTransport[Effect, Context, Adapter]

  def handler: RequestHandler[Effect, Context]

  /**
   * Creates a copy of this endpoint with added RPC bindings for all public methods of the specified API instance.
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
   * the `Context` type the endpoint-supplied ''request context'' is passed to the bound method or
   * the returned context function as its last argument.
   *
   * @param api
   *   API instance
   * @tparam Api
   *   API type (only member methods of this type are exposed)
   * @return
   *   RPC request endpoint with specified API bindings
   * @throws java.lang.IllegalArgumentException
   *   if invalid public methods are found in the API type
   */
  def bind[Api <: AnyRef](api: Api): RpcEndpoint[Node, Codec, Effect, Context, Adapter] =
    macro EndpointBind.bindMacro[Node, Codec, Effect, Context, Adapter, Api]

  /**
   * Creates a copy of this endpoint with added RPC bindings for all public methods of the specified API instance.
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
   * the `Context` type the endpoint-supplied ''request context'' is passed to the bound method or
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
   *   RPC request endpoint with specified API bindings
   * @throws java.lang.IllegalArgumentException
   *   if invalid public methods are found in the API type
   */
  def bind[Api <: AnyRef](
    api: Api, mapName: String => Iterable[String]
  ): RpcEndpoint[Node, Codec, Effect, Context, Adapter] =
    macro EndpointBind.bindMapNameMacro[Node, Codec, Effect, Context, Adapter, Api]
}

case object EndpointBind {

  def bindMacro[Node, Codec <: MessageCodec[Node], Effect[_], Context, Adapter, Api <: AnyRef](c: blackbox.Context)(
    api: c.Expr[Api]
  )(implicit
    nodeType: c.WeakTypeTag[Node],
    codecType: c.WeakTypeTag[Codec],
    effectType: c.WeakTypeTag[Effect[?]],
    contextType: c.WeakTypeTag[Context],
    adapterType: c.WeakTypeTag[Adapter],
    apiType: c.WeakTypeTag[Api],
  ): c.Expr[RpcEndpoint[Node, Codec, Effect, Context, Adapter]] = {
    import c.universe.Quasiquote
    Seq(nodeType, codecType, effectType, contextType, adapterType, apiType)

    val mapName = c.Expr[String => Seq[String]](q"""
      (name: String) => Seq(name)
    """)
    bindMapNameMacro[Node, Codec, Effect, Context, Adapter, Api](c)(api, mapName)
  }

  def bindMapNameMacro[Node, Codec <: MessageCodec[Node], Effect[_], Context, Adapter, Api <: AnyRef](
    c: blackbox.Context
  )(
    api: c.Expr[Api], mapName: c.Expr[String => Iterable[String]]
  )(implicit
    nodeType: c.WeakTypeTag[Node],
    codecType: c.WeakTypeTag[Codec],
    effectType: c.WeakTypeTag[Effect[?]],
    contextType: c.WeakTypeTag[Context],
    adapterType: c.WeakTypeTag[Adapter],
    apiType: c.WeakTypeTag[Api],
  ): c.Expr[RpcEndpoint[Node, Codec, Effect, Context, Adapter]] = {
    import c.universe.Quasiquote
    Seq(adapterType)

    // This endpoint needs to be assigned to a stable identifier due to macro expansion limitations
    c.Expr[RpcEndpoint[Node, Codec, Effect, Context, Adapter]](q"""
      import automorph.handler.{ApiRequestHandler, HandlerBinding}
      import automorph.handler.meta.HandlerBindings
      import scala.collection.immutable.ListMap

      val endpoint = ${c.prefix}
      val apiBindings = if (endpoint.handler.isInstanceOf[ApiRequestHandler[?, ?, $effectType, ?]]) {
        endpoint.handler.asInstanceOf[ApiRequestHandler[$nodeType, $codecType, $effectType, $contextType]].apiBindings
      } else {
        ListMap.empty[String, HandlerBinding[$nodeType, $effectType, $contextType]]
      }
      val newApiBindings = HandlerBindings.generate[$nodeType, $codecType, $effectType, $contextType, $apiType](
        endpoint.rpcProtocol.messageCodec, $api
      ).flatMap { binding =>
        $mapName(binding.function.name).map(_ -> binding)
      }
      val handler = ApiRequestHandler(
        endpoint.transport.effectSystem,
        endpoint.rpcProtocol,
        apiBindings ++ newApiBindings,
        endpoint.handler.discovery,
      )
      automorph.RpcEndpoint(endpoint.transport, endpoint.rpcProtocol, handler, handler.functions)
    """)
  }
}
