package automorph.handler

import automorph.RpcFunction
import automorph.RpcException.{FunctionNotFound, InvalidArguments}
import automorph.log.{LogProperties, Logging}
import automorph.spi.RequestHandler.Result
import automorph.spi.protocol.{Message, Request}
import automorph.spi.{EffectSystem, MessageCodec, RequestHandler, RpcProtocol}
import automorph.util.Extensions.EffectOps
import scala.collection.immutable.ListMap
import scala.util.{Failure, Success, Try}

/**
 * RPC request handler for bound APIs.
 *
 * Processes remote API requests and invoke bound API methods.
 *
 * Note: Consider this class to be private and do not use it. It remains public only due to Scala 2 macro limitations.
 *
 * @constructor
 *   Creates a new RPC request handler using specified effect system and RPC protocol plugins
 *   with corresponding message context type.
 * @param rpcProtocol
 *   RPC protocol plugin
 * @param effectSystem
 *   effect system plugin
 * @param apiBindings
 *   API method bindings
 * @param discovery
 *   enable automatic provision of service discovery via RPC functions returning bound API schema
 * @tparam Node
 *   message node type
 * @tparam Codec
 *   message codec plugin type
 * @tparam Effect
 *   effect type
 * @tparam Context
 *   RPC message context type
 */
final case class ApiRequestHandler[Node, Codec <: MessageCodec[Node], Effect[_], Context](
  effectSystem: EffectSystem[Effect],
  rpcProtocol: RpcProtocol[Node, Codec, Context],
  apiBindings: ListMap[String, HandlerBinding[Node, Effect, Context]] =
    ListMap[String, HandlerBinding[Node, Effect, Context]](),
  discovery: Boolean = false,
) extends RequestHandler[Effect, Context] with Logging {

  private val bindings = Option.when(discovery)(apiSchemaBindings).getOrElse(ListMap.empty) ++ apiBindings
  private implicit val system: EffectSystem[Effect] = effectSystem

  /** Bound RPC functions. */
  lazy val functions: Seq[RpcFunction] = bindings.map { case (name, binding) =>
    binding.function.copy(name = name)
  }.toSeq

  override def processRequest(requestBody: Array[Byte], context: Context, id: String): Effect[Option[Result[Context]]] =
    // Parse request
    rpcProtocol.parseRequest(requestBody, context, id).fold(
      error =>
        errorResponse(
          error.exception,
          error.message,
          responseRequired = true,
          ListMap(LogProperties.requestId -> id),
        ),
      rpcRequest => {
        // Invoke requested RPC function
        lazy val requestProperties = ListMap(LogProperties.requestId -> id) ++ rpcRequest.message.properties
        lazy val allProperties = requestProperties ++ rpcRequest.message.text.map(LogProperties.messageBody -> _)
        logger.trace(s"Received ${rpcProtocol.name} request", allProperties)
        callFunction(rpcRequest, context, requestProperties)
      },
    )

  override def discovery(discovery: Boolean): RequestHandler[Effect, Context] =
    copy(discovery = discovery)

  override def mediaType: String =
    rpcProtocol.messageCodec.mediaType

  /**
   * Calls remote function specified in a request and creates a response.
   *
   * Optional request context is used as a last remote function argument.
   *
   * @param rpcRequest
   *   RPC request
   * @param context
   *   request context
   * @param requestProperties
   *   request properties
   * @return
   *   bound RPC function call response
   */
  private def callFunction(
    rpcRequest: Request[Node, rpcProtocol.Metadata, Context],
    context: Context,
    requestProperties: => Map[String, String],
  ): Effect[Option[Result[Context]]] = {
    // Lookup bindings for the specified remote function
    val responseRequired = rpcRequest.responseRequired
    logger.debug(s"Processing ${rpcProtocol.name} request", requestProperties)
    bindings.get(rpcRequest.function).map { binding =>
      // Extract bound function argument nodes
      extractArguments(rpcRequest, binding).map { argumentNodes =>
        // Decode bound function arguments
        decodeArguments(argumentNodes, binding)
      }.map { arguments =>
        // Call bound API method
        binding.call(arguments, context)
      }.fold(
        error => errorResponse(error, rpcRequest.message, responseRequired, requestProperties),
        result => {
          // Encode bound API method result
          val contextualResultNode = result.asInstanceOf[Effect[Any]].map { resultValue =>
            encodeResult(resultValue, binding)
          }

          // Create RPC response
          resultResponse(contextualResultNode, rpcRequest, requestProperties)
        },
      )
    }.getOrElse {
      val error = FunctionNotFound(s"Function not found: ${rpcRequest.function}", None.orNull)
      errorResponse(error, rpcRequest.message, responseRequired, requestProperties)
    }
  }

  /**
   * Validates and extracts specified remote function arguments from a request.
   *
   * Optional request context is used as a last remote function argument.
   *
   * @param rpcRequest
   *   RPC request
   * @param binding
   *   remote function binding
   * @return
   *   bound function arguments
   */
  private def extractArguments(
    rpcRequest: Request[Node, ?, Context],
    binding: HandlerBinding[Node, Effect, Context],
  ): Try[Seq[Option[Node]]] = {
    // Adjust expected function parameters if it uses context as its last parameter
    val parameters = binding.function.parameters
    val parameterNames = parameters.map(_.name).dropRight(if (binding.acceptsContext) 1 else 0)

    // Identify redundant arguments
    val namedArguments = rpcRequest.arguments.flatMap(_.toOption).toMap
    val positionalArguments = rpcRequest.arguments.flatMap(_.swap.toOption)
    val argumentNames = namedArguments.keys.toSeq
    val matchedNamedArguments = argumentNames.intersect(parameterNames)
    val requiredPositionalArguments = parameterNames.size - matchedNamedArguments.size
    val redundantNames = argumentNames.diff(parameterNames)
    val redundantIndices = Range(requiredPositionalArguments, positionalArguments.size)

    // Assemble required arguments
    if (redundantNames.size + redundantIndices.size > 0) {
      val redundantIdentifiers = redundantNames ++ redundantIndices.map(_.toString)
      Failure(new IllegalArgumentException(s"Redundant arguments: ${redundantIdentifiers.mkString(", ")}"))
    } else {
      Success(parameterNames.foldLeft(Seq[Option[Node]]() -> 0) { case ((arguments, index), name) =>
        val (argument, newIndex) = namedArguments.get(name) match {
          case Some(value) => Some(value) -> index
          case _ if index < positionalArguments.size => Some(positionalArguments(index)) -> (index + 1)
          case _ => None -> index
        }
        (arguments :+ argument) -> newIndex
      }._1)
    }
  }

  /**
   * Decodes specified remote function argument nodes into values.
   *
   * @param argumentNodes
   *   remote function argument nodes
   * @param binding
   *   remote function binding
   * @return
   *   remote function arguments
   */
  private def decodeArguments(
    argumentNodes: Seq[Option[Node]],
    binding: HandlerBinding[Node, Effect, Context],
  ): Seq[Any] =
    binding.function.parameters.zip(argumentNodes).map { case (parameter, argumentNode) =>
      val decodeArgument = binding.argumentDecoders.getOrElse(
        parameter.name,
        throw new IllegalStateException(s"Missing method parameter decoder: ${parameter.name}"),
      )
      Try(Option(decodeArgument(argumentNode)).get).recoverWith { case error =>
        val message = s"${argumentNode.fold("Missing")(_ => "Malformed")} argument: ${parameter.name}"
        Failure(InvalidArguments(message, error))
      }.get
    }

  /**
   * Decodes specified remote function argument nodes into values.
   *
   * @param result
   *   remote function result
   * @param binding
   *   remote function binding
   * @return
   *   remote function result node
   */
  private def encodeResult(result: Any, binding: HandlerBinding[Node, Effect, Context]): (Node, Option[Context]) =
    Try(binding.encodeResult(result)).recoverWith { case error =>
      Failure(new IllegalArgumentException("Malformed result", error))
    }.get

  /**
   * Creates a response for remote function call result.
   *
   * @param callResult
   *   remote function call result
   * @param rpcRequest
   *   RPC request
   * @param requestProperties
   *   request properties
   * @return
   *   bound function call RPC response
   */
  private def resultResponse(
    callResult: Effect[(Node, Option[Context])],
    rpcRequest: Request[Node, rpcProtocol.Metadata, Context],
    requestProperties: => Map[String, String],
  ): Effect[Option[Result[Context]]] =
    callResult.either.flatMap { result =>
      result.fold(
        error => logger.error(s"Failed to process ${rpcProtocol.name} request", error, requestProperties),
        _ => logger.info(s"Processed ${rpcProtocol.name} request", requestProperties),
      )

      // Create response
      if (rpcRequest.responseRequired) {
        response(result.toTry, rpcRequest.message, requestProperties)
      } else {
        effectSystem.successful(None)
      }
    }

  /**
   * Creates a handler result containing an RPC response for the specified error.
   *
   * @param error
   *   exception
   * @param message
   *   RPC message
   * @param responseRequired
   *   true if response is required
   * @param requestProperties
   *   request properties
   * @return
   *   handler result
   */
  private def errorResponse(
    error: Throwable,
    message: Message[rpcProtocol.Metadata],
    responseRequired: Boolean,
    requestProperties: => Map[String, String],
  ): Effect[Option[Result[Context]]] = {
    logger.error(s"Failed to process ${rpcProtocol.name} request", error, requestProperties)
    Option.when(responseRequired) {
      response(Failure(error), message, requestProperties)
    }.getOrElse(effectSystem.successful(None))
  }

  /**
   * Creates a handler result containing an RPC response for the specified resul value.
   *
   * @param result
   *   a call result on success or an exception on failure
   * @param message
   *   RPC message
   * @param requestProperties
   *   request properties
   * @return
   *   handler result
   */
  private def response(
    result: Try[(Node, Option[Context])],
    message: Message[rpcProtocol.Metadata],
    requestProperties: => Map[String, String],
  ): Effect[Option[Result[Context]]] =
    rpcProtocol.createResponse(result.map(_._1), message.metadata).fold(
      error => effectSystem.failed(error),
      rpcResponse => {
        val responseBody = rpcResponse.message.body
        lazy val allProperties = rpcResponse.message.properties ++ requestProperties ++
          rpcResponse.message.text.map(LogProperties.messageBody -> _)
        logger.trace(s"Sending ${rpcProtocol.name} response", allProperties)
        effectSystem.successful(
          Some(Result(responseBody, result.failed.toOption, result.toOption.flatMap(_._2)))
        )
      },
    )

  private def apiSchemaBindings: ListMap[String, HandlerBinding[Node, Effect, Context]] =
    ListMap(rpcProtocol.apiSchemas.map { apiSchema =>
      val apiSchemaFunctions = rpcProtocol.apiSchemas.filter { apiSchema =>
        !apiBindings.contains(apiSchema.function.name)
      }.map(_.function) ++ apiBindings.values.map(_.function)
      apiSchema.function.name -> HandlerBinding[Node, Effect, Context](
        apiSchema.function,
        Map.empty,
        result => result.asInstanceOf[Node] -> None,
        (_, _) => effectSystem.successful(apiSchema.describe(apiSchemaFunctions)),
        acceptsContext = false,
      )
    }*)

  override def toString: String = {
    val plugins = Map[String, Any]("system" -> effectSystem, "protocol" -> rpcProtocol).map { case (name, plugin) =>
      s"$name = ${plugin.getClass.getName}"
    }.mkString(", ")
    s"${this.getClass.getName}($plugins)"
  }
}
