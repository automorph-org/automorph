package automorph.client.meta

import automorph.client.RemoteCall
import automorph.spi.{MessageCodec, RpcProtocol}
import scala.language.experimental.macros
import scala.reflect.macros.blackbox

/**
 * Client API method bindings layer.
 *
 * @tparam Node
 *   message node type
 * @tparam Codec
 *   message codec plugin type
 * @tparam Effect
 *   effect type
 * @tparam Context
 *   RPC message context type
 */
private[automorph] trait ClientBind[Node, Codec <: MessageCodec[Node], Effect[_], Context] {

  def rpcProtocol: RpcProtocol[Node, Codec, Context]

  /**
   * Creates a RPC API proxy with RPC bindings for all public methods of the specified API type.
   *
   * The binding generation fails if any public API method has one of the following properties:
   *   - does not return the specified effect type
   *   - is overloaded
   *   - has type parameters
   *   - is inline
   *
   * If the last parameter of bound method is of `Context` type or returns a context function accepting
   * the `Context` type the caller-supplied request context is passed to the underlying message transport plugin.
   *
   * @tparam Api
   *   API trait type (classes are not supported)
   * @return
   *   RPC API proxy
   * @throws java.lang.IllegalArgumentException
   *   if invalid public functions are found in the API type
   */
  def bind[Api <: AnyRef]: Api =
    macro ClientBind.bindMacro[Node, Codec, Effect, Context, Api]

  /**
   * Creates a remote API proxy with RPC bindings for all public methods of the specified API type.
   *
   * The binding generation fails if any public API method has one of the following properties:
   *   - does not return the specified effect type
   *   - is overloaded
   *   - has type parameters
   *   - is inline
   *
   * If the last parameter of bound method is of `Context` type or returns a context function accepting
   * the `Context` type the caller-supplied request context is passed to the underlying message transport plugin.
   *
   * RPC functions defined by bound API methods are invoked with their names transformed via the `mapName` function.
   *
   * @param mapName
   *   maps bound API method name to the invoked RPC function name
   * @tparam Api
   *   remote API trait type (classes are not supported)
   * @return
   *   remote API proxy
   * @throws java.lang.IllegalArgumentException
   *   if invalid public functions are found in the API type
   */
  def bind[Api <: AnyRef](mapName: String => String): Api =
    macro ClientBind.bindMapNamesMacro[Node, Codec, Effect, Context, Api]

  /**
   * Creates a remote API function call proxy.
   *
   * Uses the remote function name and arguments to send an RPC request and extracts a result value or an error
   * from the received RPC response.
   *
   * @param function
   *   remote function name
   * @tparam Result
   *   result type
   * @return
   *   specified remote function call proxy
   * @throws RpcException
   *   on RPC error
   */
  def call[Result](function: String): RemoteCall[Node, Codec, Effect, Context, Result] =
    macro ClientBind.callMacro[Node, Codec, Effect, Context, Result]

  def performCall[Result](
     function: String,
     arguments: Seq[(String, Node)],
     decodeResult: (Node, Context) => Result,
     requestContext: Option[Context],
   ): Effect[Result]
}

object ClientBind {

  def bindMacro[Node, Codec <: MessageCodec[Node], Effect[_], Context, Api <: AnyRef](c: blackbox.Context)(implicit
    nodeType: c.WeakTypeTag[Node],
    codecType: c.WeakTypeTag[Codec],
    effectType: c.WeakTypeTag[Effect[?]],
    contextType: c.WeakTypeTag[Context],
    apiType: c.WeakTypeTag[Api],
  ): c.Expr[Api] = {
    import c.universe.Quasiquote
    Seq(nodeType, codecType, effectType, contextType, apiType)

    val mapName = c.Expr[String => String](q"""
      identity
    """)
    bindMapNamesMacro[Node, Codec, Effect, Context, Api](c)(mapName)
  }

  def bindMapNamesMacro[Node, Codec <: MessageCodec[Node], Effect[_], Context, Api <: AnyRef](c: blackbox.Context)(
    mapName: c.Expr[String => String]
  )(implicit
    nodeType: c.WeakTypeTag[Node],
    codecType: c.WeakTypeTag[Codec],
    effectType: c.WeakTypeTag[Effect[?]],
    contextType: c.WeakTypeTag[Context],
    apiType: c.WeakTypeTag[Api],
  ): c.Expr[Api] = {
    import c.universe.Quasiquote

    // This client needs to be assigned to a stable identifier due to macro expansion limitations
    c.Expr[Api](q"""
      // Generate API function bindings
      val client = ${c.prefix}
      val bindings = automorph.client.meta.ClientBindings
        .generate[$nodeType, $codecType, $effectType, $contextType, $apiType](
          client.rpcProtocol.messageCodec
        ).map { binding =>
          binding.function.name -> binding
        }.toMap

      // Create remote API proxy
      java.lang.reflect.Proxy.newProxyInstance(
        this.getClass.getClassLoader,
        Array(classOf[$apiType]),
        (_, method, arguments) =>

          // Lookup bindings for the specified method
          bindings.get(method.getName).map { binding =>

            // Adjust RPC function arguments if it accepts request context as its last parameter
            val callArguments = Option(arguments).getOrElse(Array.empty[AnyRef])
            val (argumentValues, requestContext) =
              if (binding.acceptsContext && callArguments.nonEmpty) {
                callArguments.dropRight(1).toSeq -> Some(callArguments.last.asInstanceOf[$contextType])
              } else {
                callArguments.toSeq -> None
              }

            // Encode RPC function arguments
            val argumentNodes = binding.function.parameters.zip(argumentValues).map { case (parameter, argument) =>
              val encodeArgument = binding.argumentEncoders.getOrElse(
                parameter.name,
                throw new IllegalStateException("Missing method parameter encoder: " + parameter.name)
              )
              parameter.name -> scala.util.Try(encodeArgument(argument)).recoverWith { case error =>
                scala.util.Failure(automorph.RpcException.InvalidRequest(
                  "Malformed argument: " + parameter.name,
                  error
                ))
              }.get
            }

            // Perform the RPC call
            client.performCall(
              $mapName(method.getName),
              argumentNodes,
              (resultNode, responseContext) => binding.decodeResult(resultNode, responseContext),
              requestContext)
          }.getOrElse(throw new UnsupportedOperationException("Invalid method: " + method.getName))
      ).asInstanceOf[$apiType]
    """)
  }

  def callMacro[
    Node,
    Codec <: MessageCodec[Node],
    Effect[_],
    Context,
    Result,
  ](c: blackbox.Context)(function: c.Expr[String]): c.Expr[RemoteCall[Node, Codec, Effect, Context, Result]] = {
    import c.universe.Quasiquote

    // This client needs to be assigned to a stable identifier due to macro expansion limitations
    c.Expr[RemoteCall[Node, Codec, Effect, Context, Result]](q"""
      val client = ${c.prefix}
      automorph.client.RemoteCall($function, client.rpcProtocol.messageCodec, client.performCall)
    """)
  }
}
