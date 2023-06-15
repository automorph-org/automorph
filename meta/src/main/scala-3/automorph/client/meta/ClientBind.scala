package automorph.client.meta

import automorph.RpcException.InvalidRequest
import automorph.client.{RemoteCall, RemoteTell}
import automorph.spi.{MessageCodec, RpcProtocol}

import java.lang.reflect.Proxy
import scala.compiletime.summonInline
import scala.reflect.ClassTag
import scala.util.{Failure, Try}

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
private[automorph] trait ClientBind[Node, Codec <: MessageCodec[Node], Effect[_], Context]:

  def rpcProtocol: RpcProtocol[Node, Codec, Context]

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
   * @param mapName
   *   maps API method name to the invoked RPC function name
   * @tparam Api
   *   API trait type (classes are not supported)
   * @return
   *   RPC API proxy
   * @throws java.lang.IllegalArgumentException
   *   if invalid public methods are found in the API type
   */
  inline def bind[Api <: AnyRef]: Api =
    bind[Api](identity)

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
   *   if invalid public methods are found in the API type
   */
  inline def bind[Api <: AnyRef](mapName: String => String): Api =
    // Generate API method bindings
    val bindings = ClientBindings.generate[Node, Codec, Effect, Context, Api](
      rpcProtocol.messageCodec
    ).map { binding =>
      binding.function.name -> binding
    }.toMap

    // Create remote API proxy
    val classTag = summonInline[ClassTag[Api]]
    Proxy.newProxyInstance(
      this.getClass.getClassLoader,
      Array(classTag.runtimeClass),
      (_, method, arguments) =>

        // Lookup bindings for the specified method
        bindings.get(method.getName).map { binding =>

          // Adjust RPC function arguments if it accepts request context as its last parameter
          val callArguments = Option(arguments).getOrElse(Array.empty[AnyRef])
          val (argumentValues, requestContext) =
            if binding.acceptsContext && callArguments.nonEmpty then
              callArguments.dropRight(1).toSeq -> Some(callArguments.last.asInstanceOf[Context])
            else callArguments.toSeq -> None

          // Encode RPC function arguments
          val argumentNodes = binding.function.parameters.zip(argumentValues).map { (parameter, argument) =>
            val encodeArgument = binding.argumentEncoders.getOrElse(
              parameter.name,
              throw new IllegalStateException(s"Missing method parameter encoder: ${parameter.name}"),
            )
            parameter.name -> Try(encodeArgument(argument)).recoverWith { case error =>
              Failure(InvalidRequest(s"Malformed argument: ${parameter.name}", error))
            }.get
          }

          // Perform the RPC call
          performCall(
            mapName(method.getName),
            argumentNodes,
            (resultNode, responseContext) => binding.decodeResult(resultNode, responseContext),
            requestContext,
          )
        }.getOrElse(throw UnsupportedOperationException(s"Invalid method: ${method.getName}")),
    ).asInstanceOf[Api]

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
  inline def call[Result](function: String): RemoteCall[Node, Codec, Effect, Context, Result] =
    RemoteCall(function, rpcProtocol.messageCodec, performCall)

  def performCall[Result](
    function: String,
    arguments: Seq[(String, Node)],
    decodeResult: (Node, Context) => Result,
    requestContext: Option[Context],
  ): Effect[Result]
