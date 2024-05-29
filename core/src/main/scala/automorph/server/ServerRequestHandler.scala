package automorph.server

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
 * Server RPC request handler for bound APIs.
 *
 * Processes remote API requests and invoke bound API methods.
 *
 * @constructor
 *   Creates a new RPC request handler using specified effect system and RPC protocol plugins with corresponding message
 *   context type.
 * @param rpcProtocol
 *   RPC protocol plugin
 * @param effectSystem
 *   effect system plugin
 * @param discovery
 *   enable automatic provision of service discovery via RPC functions returning bound API schema
 * @param apiBindings
 *   API method bindings
 * @tparam Value
 *   message node type
 * @tparam Codec
 *   message codec plugin type
 * @tparam Effect
 *   effect type
 * @tparam Context
 *   RPC message context type
 */
final private[automorph] case class ServerRequestHandler[Value, Codec <: MessageCodec[Value], Effect[_], Context](
  effectSystem: EffectSystem[Effect],
  rpcProtocol: RpcProtocol[Value, Codec, Context],
  discovery: Boolean = false,
  apiBindings: ListMap[String, ServerBinding[Value, Effect, Context]] =
    ListMap[String, ServerBinding[Value, Effect, Context]](),
) extends RequestHandler[Effect, Context] with Logging {

  /** Bound RPC functions. */
  lazy val functions: Seq[RpcFunction] = bindings.map { case (name, binding) =>
    binding.function.copy(name = name)
  }.toSeq
  private val bindings = Option.when(discovery)(apiSchemaBindings).getOrElse(ListMap.empty) ++ apiBindings
  implicit private val system: EffectSystem[Effect] = effectSystem

  override def processRequest(body: Array[Byte], context: Context, id: String): Effect[Option[Result[Context]]] =
    // Parse request
    rpcProtocol.parseRequest(body, context, id).fold(
      error =>
        errorResponse(
          error.exception,
          error.message,
          responseRequired = true,
          ListMap(LogProperties.requestId -> id),
        ),
      { rpcRequest =>
        // Invoke requested RPC function
        lazy val requestProperties = ListMap(LogProperties.requestId -> rpcRequest.id) ++ rpcRequest.message.properties
        lazy val allProperties = requestProperties ++ getMessageBody(rpcRequest.message)
        logger.trace(s"Received ${rpcProtocol.name} request", allProperties)
        callFunction(rpcRequest, context, requestProperties)
      },
    )

  override def discovery(discovery: Boolean): RequestHandler[Effect, Context] =
    copy(discovery = discovery)

  override def mediaType: String =
    rpcProtocol.messageCodec.mediaType

  override def toString: String = {
    val plugins = Map[String, Any]("system" -> effectSystem, "protocol" -> rpcProtocol).map { case (name, plugin) =>
      s"$name = ${plugin.getClass.getName}"
    }.mkString(", ")
    s"${this.getClass.getName}($plugins)"
  }

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
    rpcRequest: Request[Value, rpcProtocol.Metadata, Context],
    context: Context,
    requestProperties: => Map[String, String],
  ): Effect[Option[Result[Context]]] = {
    // Lookup bindings for the specified remote function
    val responseRequired = rpcRequest.respond
    logger.debug(s"Processing ${rpcProtocol.name} request", requestProperties)
    bindings.get(rpcRequest.function).map { binding =>
      // Extract bound function argument nodes
      extractArguments(rpcRequest, binding).map { argumentValues =>
        // Decode bound function arguments
        decodeArguments(argumentValues, binding)
      }.map { arguments =>
        // Call bound API method
        binding.call(arguments, context)
      }.fold(
        error => errorResponse(error, rpcRequest.message, responseRequired, requestProperties),
        { result =>
          // Encode bound API method result
          val contextualResultValue = result.asInstanceOf[Effect[Any]].map { resultValue =>
            encodeResult(resultValue, binding)
          }

          // Create RPC response
          resultResponse(contextualResultValue, rpcRequest, requestProperties)
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
    rpcRequest: Request[Value, ?, Context],
    binding: ServerBinding[Value, Effect, Context],
  ): Try[Seq[Option[Value]]] = {
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
      Success(parameterNames.foldLeft(Seq[Option[Value]]() -> 0) { case ((arguments, currentIndex), name) =>
        val (argument, newIndex) = namedArguments.get(name) match {
          case Some(value) => Some(value) -> currentIndex
          case _ if currentIndex < positionalArguments.size =>
            Some(positionalArguments(currentIndex)) -> (currentIndex + 1)
          case _ => None -> currentIndex
        }
        (arguments :+ argument) -> newIndex
      }._1)
    }
  }

  /**
   * Decodes specified remote function argument nodes into values.
   *
   * @param argumentValues
   *   remote function argument nodes
   * @param binding
   *   remote function binding
   * @return
   *   remote function arguments
   */
  private def decodeArguments(
    argumentValues: Seq[Option[Value]],
    binding: ServerBinding[Value, Effect, Context],
  ): Seq[Any] =
    binding.function.parameters.zip(argumentValues).map { case (parameter, argumentValue) =>
      val decodeArgument = binding.argumentDecoders.getOrElse(
        parameter.name,
        throw new IllegalStateException(s"Missing method parameter decoder: ${parameter.name}"),
      )
      Try(Option(decodeArgument(argumentValue)).get).recoverWith { case error =>
        val message = s"${argumentValue.fold("Missing")(_ => "Malformed")} argument: ${parameter.name}"
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
  private def encodeResult(result: Any, binding: ServerBinding[Value, Effect, Context]): (Value, Option[Context]) =
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
    callResult: Effect[(Value, Option[Context])],
    rpcRequest: Request[Value, rpcProtocol.Metadata, Context],
    requestProperties: => Map[String, String],
  ): Effect[Option[Result[Context]]] =
    callResult.flatFold(
      { error =>
        logger.error(s"Failed to process ${rpcProtocol.name} request", error, requestProperties)
        createResponse(Failure(error), rpcRequest, requestProperties)
      },
      { result =>
        logger.info(s"Processed ${rpcProtocol.name} request", requestProperties)
        createResponse(Success(result), rpcRequest, requestProperties)
      },
    )

  /**
   * Creates a handler result containing an RPC response for the specified result.
   *
   * @param result
   *   remote function call result
   * @param rpcRequest
   *   RPC request
   * @param requestProperties
   *   request properties
   * @return
   *   handler result
   */
  private def createResponse(
    result: Try[(Value, Option[Context])],
    rpcRequest: Request[Value, rpcProtocol.Metadata, Context],
    requestProperties: => Map[String, String],
  ): Effect[Option[Result[Context]]] =
    if (rpcRequest.respond) {
      response(result, rpcRequest.message, requestProperties)
    } else {
      effectSystem.successful(None)
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
   * Creates a handler result containing an RPC response for the specified result value.
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
    result: Try[(Value, Option[Context])],
    message: Message[rpcProtocol.Metadata],
    requestProperties: => Map[String, String],
  ): Effect[Option[Result[Context]]] =
    rpcProtocol.createResponse(result.map(_._1), message.metadata).fold(
      error => effectSystem.failed(error),
      { rpcResponse =>
        val responseBody = rpcResponse.message.body
        lazy val allProperties = rpcResponse.message.properties ++ requestProperties ++
          getMessageBody(rpcResponse.message)
        logger.trace(s"Sending ${rpcProtocol.name} response", allProperties)
        effectSystem.successful(
          Some(Result(responseBody, result.failed.toOption, result.toOption.flatMap(_._2)))
        )
      },
    )

  private def getMessageBody(message: Message[?]): Option[(String, String)] =
    message.text.map(LogProperties.messageBody -> _)

  private def apiSchemaBindings: ListMap[String, ServerBinding[Value, Effect, Context]] =
    ListMap(rpcProtocol.apiSchemas.map { apiSchema =>
      val apiSchemaFunctions = rpcProtocol.apiSchemas.filter { apiSchema =>
        !apiBindings.contains(apiSchema.function.name)
      }.map(_.function) ++ apiBindings.values.map(_.function)
      apiSchema.function.name -> ServerBinding[Value, Effect, Context](
        apiSchema.function,
        Map.empty,
        result => result.asInstanceOf[Value] -> None,
        (_, _) => effectSystem.successful(apiSchema.describe(apiSchemaFunctions)),
        acceptsContext = false,
      )
    }*)
}
