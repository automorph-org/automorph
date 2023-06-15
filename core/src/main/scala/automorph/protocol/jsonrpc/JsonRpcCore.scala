package automorph.protocol.jsonrpc

import automorph.RpcFunction
import automorph.RpcException.InvalidResponse
import automorph.schema.openapi.{RpcSchema, Schema}
import automorph.schema.{OpenApi, OpenRpc}
import automorph.protocol.JsonRpcProtocol
import automorph.spi.MessageCodec
import automorph.spi.protocol
import automorph.spi.protocol.{ApiSchema, ParseError}
import automorph.util.Extensions.ThrowableOps
import scala.annotation.nowarn
import scala.util.{Failure, Success, Try}

/**
 * JSON-RPC protocol core logic.
 *
 * @tparam Node
 *   message node type
 * @tparam Codec
 *   message codec plugin type
 * @tparam Context
 *   RPC message context type
 */
private[automorph] trait JsonRpcCore[Node, Codec <: MessageCodec[Node], Context] {
  this: JsonRpcProtocol[Node, Codec, Context] =>

  /** JSON-RPC message metadata. */
  type Metadata = Option[Message.Id]

  private lazy val errorSchema: Schema = Schema(
    Some(OpenApi.objectType),
    Some(OpenApi.errorTitle),
    Some(s"$name ${OpenApi.errorTitle}"),
    Some(Map(
      "error" -> Schema(
        Some("string"),
        Some("error"),
        Some("Failed function call error details"),
        Some(Map(
          "code" -> Schema(Some("integer"), Some("code"), Some("Error code")),
          "message" -> Schema(Some("string"), Some("message"), Some("Error message")),
          "data" -> Schema(Some("object"), Some("data"), Some("Additional error information")),
        )),
        Some(List("message")),
      )
    )),
    Some(List("error")),
  )
  val name: String = "JSON-RPC"
  private val unknownId = Right("[unknown]")

  override def createRequest(
    function: String,
    arguments: Iterable[(String, Node)],
    responseRequired: Boolean,
    requestContext: Context,
    requestId: String,
  ): Try[protocol.Request[Node, Metadata, Context]] = {
    // Create request
    require(requestId.nonEmpty, "Empty request identifier")
    val id = Option.when(responseRequired)(Right(requestId).withLeft[BigDecimal])
    val requestMessage = Request(id, function, Right(arguments.toMap)).message

    // Serialize request
    val messageText = () => Some(messageCodec.text(encodeMessage(requestMessage)))
    Try(messageCodec.serialize(encodeMessage(requestMessage))).recoverWith { case error =>
      Failure(JsonRpcException("Malformed request", ErrorType.ParseError.code, None, error))
    }.map { messageBody =>
      val message = protocol.Message(id, messageBody, requestMessage.properties, messageText)
      val requestArguments = arguments.map(Right.apply[Node, (String, Node)]).toSeq
      protocol.Request(message, function, requestArguments, responseRequired, requestId, requestContext)
    }
  }

  override def parseRequest(
    requestBody: Array[Byte],
    requestContext: Context,
    requestId: String,
  ): Either[ParseError[Metadata], protocol.Request[Node, Metadata, Context]] =
    // Deserialize request
    Try(decodeMessage(messageCodec.deserialize(requestBody))).fold(
      error => Left(ParseError(
        JsonRpcException("Malformed request", ErrorType.ParseError.code, None, error),
        protocol.Message(None, requestBody)
      )),
      requestMessage => {
        // Validate request
        val messageText = () => Some(messageCodec.text(encodeMessage(requestMessage)))
        val message = protocol.Message(requestMessage.id, requestBody, requestMessage.properties, messageText)
        Try(Request(requestMessage)).fold(
          error => Left(ParseError(error, message)),
          request => {
            val requestArguments = request.params
              .fold(_.map(Left.apply[Node, (String, Node)]), _.map(Right.apply[Node, (String, Node)]).toSeq)
            Right(protocol.Request(
              message, request.method, requestArguments, request.id.isDefined, requestId, requestContext
            ))
          },
        )
      },
    )

  override def createResponse(result: Try[Node], requestMetadata: Metadata): Try[protocol.Response[Node, Metadata]] = {
    // Create response
    val id = requestMetadata.getOrElse(unknownId)
    val responseMessage = result.fold(
      error => {
        val responseError = error match {
          case JsonRpcException(message, code, data, _) => ResponseError(message, code, data.asInstanceOf[Option[Node]])
          case _ =>
            // Assemble error details
            val trace = error.trace
            val message = trace.headOption.getOrElse("Unknown error")
            val code = mapException(error).code
            val data = Some(encodeStrings(trace.drop(1).toList))
            ResponseError(message, code, data)
        }
        Response[Node](id, None, Some(responseError)).message
      },
      resultValue => Response(id, Some(resultValue), None).message,
    )

    // Serialize response
    val messageText = () => Some(messageCodec.text(encodeMessage(responseMessage)))
    Try(messageCodec.serialize(encodeMessage(responseMessage))).recoverWith { case error =>
      Failure(JsonRpcException("Malformed response", ErrorType.ParseError.code, None, error))
    }.map { messageBody =>
      val message = protocol.Message(Option(id), messageBody, responseMessage.properties, messageText)
      protocol.Response(result, message)
    }
  }

  @nowarn("msg=used")
  override def parseResponse(
    responseBody: Array[Byte],
    responseContext: Context,
  ): Either[ParseError[Metadata], protocol.Response[Node, Metadata]] =
    // Deserialize response
    Try(decodeMessage(messageCodec.deserialize(responseBody))).fold(
      error => Left(ParseError(
        JsonRpcException("Malformed response", ErrorType.ParseError.code, None, error),
        protocol.Message(None, responseBody)
      )),
      responseMessage => {
        // Validate response
        val messageText = () => Some(messageCodec.text(encodeMessage(responseMessage)))
        val message = protocol.Message(responseMessage.id, responseBody, responseMessage.properties, messageText)
        Try(Response(responseMessage)).fold(
          error =>
            Left(ParseError(JsonRpcException("Malformed response", ErrorType.ParseError.code, None, error), message)),
          response =>
            // Check for error
            response.error.fold(
              // Check for result
              response.result match {
                case None => Left(ParseError(InvalidResponse("Invalid result", None.orNull), message))
                case Some(result) => Right(protocol.Response(Success(result), message))
              }
            )(error => Right(protocol.Response(Failure(mapError(error.message, error.code)), message))),
        )
      },
    )

  override def apiSchemas: Seq[ApiSchema[Node]] =
    Seq(
      ApiSchema(
        RpcFunction(JsonRpcProtocol.openApiFunction, Seq(), OpenApi.getClass.getSimpleName, None),
        functions => encodeOpenApi(openApi(functions)),
      ),
      ApiSchema(
        RpcFunction(JsonRpcProtocol.openRpcFunction, Seq(), OpenRpc.getClass.getSimpleName, None),
        functions => encodeOpenRpc(openRpc(functions)),
      )
    )

  /**
   * Creates a copy of this protocol with specified message contex type.
   *
   * @tparam NewContext
   *   RPC message context type
   * @return
   *   JSON-RPC protocol
   */
  def context[NewContext]: JsonRpcProtocol[Node, Codec, NewContext] =
    copy()

  /**
   * Creates a copy of this protocol with specified exception to JSON-RPC error mapping.
   *
   * @param exceptionToError
   *   maps an exception classs to a corresponding JSON-RPC error type
   * @return
   *   JSON-RPC protocol
   */
  def mapException(exceptionToError: Throwable => ErrorType): JsonRpcProtocol[Node, Codec, Context] =
    copy(mapException = exceptionToError)

  /**
   * Creates a copy of this protocol with specified JSON-RPC error to exception mapping.
   *
   * @param errorToException
   *   maps a JSON-RPC error to a corresponding exception
   * @return
   *   JSON-RPC protocol
   */
  def mapError(errorToException: (String, Int) => Throwable): JsonRpcProtocol[Node, Codec, Context] =
    copy(mapError = errorToException)

  /**
   * Creates a copy of this protocol with specified named arguments setting.
   *
   * @param namedArguments
   *   if true, pass arguments by name, if false pass arguments by position
   * @see
   *   [[https://www.jsonrpc.org/specification#parameter_structures Protocol specification]]
   * @return
   *   JSON-RPC protocol
   */
  def namedArguments(namedArguments: Boolean): JsonRpcProtocol[Node, Codec, Context] =
    copy(namedArguments = namedArguments)

  /**
   * Creates a copy of this protocol with given OpenRPC description transformation.
   *
   * @param mapOpenRpc
   *   transforms generated OpenRPC schema
   * @return
   *   JSON-RPC protocol
   */
  def mapOpenRpc(mapOpenRpc: OpenRpc => OpenRpc): JsonRpcProtocol[Node, Codec, Context] =
    copy(mapOpenRpc = mapOpenRpc)

  /**
   * Creates a copy of this protocol with given OpenAPI description transformation.
   *
   * @param mapOpenApi
   *   transforms generated OpenAPI schema or removes the service discovery method if the result is None
   * @return
   *   JSON-RPC protocol
   */
  def mapOpenApi(mapOpenApi: OpenApi => OpenApi): JsonRpcProtocol[Node, Codec, Context] =
    copy(mapOpenApi = mapOpenApi)

  private def openRpc(functions: Iterable[RpcFunction]): OpenRpc =
    mapOpenRpc(OpenRpc(functions))

  private def openApi(functions: Iterable[RpcFunction]): OpenApi = {
    val functionSchemas = functions.map { function =>
      function -> RpcSchema(requestSchema(function), resultSchema(function), errorSchema)
    }
    mapOpenApi(OpenApi(functionSchemas))
  }


  private def requestSchema(function: RpcFunction): Schema =
    Schema(
      Some(OpenApi.objectType),
      Some(OpenApi.requestTitle),
      Some(s"$name ${OpenApi.requestTitle}"),
      Some(Map(
        "jsonrpc" -> Schema(Some("string"), Some("jsonrpc"), Some("Protocol version (must be 2.0)")),
        "function" -> Schema(Some("string"), Some("function"), Some("Invoked function name")),
        "params" -> Schema(
          Some(OpenApi.objectType),
          Some(function.name),
          Some(OpenApi.argumentsDescription),
          Option(Schema.parameters(function)).filter(_.nonEmpty),
          Option(Schema.requiredParameters(function).toList).filter(_.nonEmpty),
        ),
        "id" -> Schema(
          Some("integer"),
          Some("id"),
          Some("Call identifier, a request without and identifier is considered to be a notification"),
        ),
      )),
      Some(List("jsonrpc", "function", "params")),
    )

  private def resultSchema(function: RpcFunction): Schema =
    Schema(
      Some(OpenApi.objectType),
      Some(OpenApi.resultTitle),
      Some(s"$name ${OpenApi.resultTitle}"),
      Some(Map(OpenApi.resultName -> Schema.result(function))),
      Some(List(OpenApi.resultName)),
    )
}
