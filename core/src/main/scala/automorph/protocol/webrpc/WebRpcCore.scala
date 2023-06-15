package automorph.protocol.webrpc

import automorph.RpcException.{InvalidRequest, InvalidResponse}
import automorph.RpcFunction
import automorph.protocol.WebRpcProtocol
import automorph.protocol.webrpc.Message.Request
import automorph.schema.OpenApi
import automorph.schema.openapi.{RpcSchema, Schema}
import automorph.spi.{MessageCodec, protocol}
import automorph.spi.protocol.{ApiSchema, ParseError}
import automorph.transport.http.{HttpContext, HttpMethod}
import automorph.util.Extensions.ThrowableOps
import scala.annotation.nowarn
import scala.util.{Failure, Success, Try}

/**
 * Web-RPC protocol core logic.
 *
 * @tparam Node
 *   message node type
 * @tparam Codec
 *   message codec plugin type
 * @tparam Context
 *   RPC message context type
 */
private[automorph] trait WebRpcCore[Node, Codec <: MessageCodec[Node], Context <: HttpContext[?]] {
  this: WebRpcProtocol[Node, Codec, Context] =>

  /** Web-RPC message metadata. */
  type Metadata = Unit

  private val functionSeparator = "^/+".r
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
        )),
        Some(List("code", "message")),
      )
    )),
    Some(List("error")),
  )

  val name: String = "Web-RPC"

  override def createRequest(
    function: String,
    arguments: Iterable[(String, Node)],
    responseRequired: Boolean,
    requestContext: Context,
    requestId: String,
  ): Try[protocol.Request[Node, Metadata, Context]] = {
    // Create request
    val request = arguments.toMap
    val requestProperties =
      Map("Type" -> MessageType.Call.toString, "Function" -> function, "Arguments" -> arguments.size.toString)

    // Serialize request
    val messageText = () => Some(messageCodec.text(encodeRequest(request)))
    Try(messageCodec.serialize(encodeRequest(request))).recoverWith { case error =>
      Failure(InvalidRequest("Malformed request", error))
    }.map { messageBody =>
      val message = protocol.Message((), messageBody, requestProperties, messageText)
      val requestArguments = arguments.map(Right.apply[Node, (String, Node)]).toSeq
      val requestPath = s"${requestContext.path.getOrElse("")}/$function"
      val functionRequestContext = requestContext.path(requestPath).asInstanceOf[Context]
      protocol.Request(message, function, requestArguments, responseRequired, requestId, functionRequestContext)
    }
  }

  override def parseRequest(
    requestBody: Array[Byte],
    requestContext: Context,
    requestId: String,
  ): Either[ParseError[Metadata], protocol.Request[Node, Metadata, Context]] =
    retrieveRequest(requestBody, requestContext).flatMap { request =>
      // Validate request
      val messageText = () => Some(messageCodec.text(encodeRequest(request)))
      val requestProperties = Map("Type" -> MessageType.Call.toString, "Arguments" -> request.size.toString)
      requestContext.path.map { path =>
        if (path.startsWith(pathPrefix) && path.length > pathPrefix.length) {
          val function = functionSeparator.replaceFirstIn(path.substring(pathPrefix.length, path.length), "")
          val message = protocol.Message((), requestBody, requestProperties ++ Seq("Function" -> function), messageText)
          val requestArguments = request.map(Right.apply[Node, (String, Node)]).toSeq
          Right(
            protocol.Request(message, function, requestArguments, responseRequired = true, requestId, requestContext)
          )
        } else {
          val message = protocol.Message((), requestBody, requestProperties, messageText)
          Left(ParseError(InvalidRequest(s"Invalid URL path: $path"), message))
        }
      }.getOrElse {
        val message = protocol.Message((), requestBody, requestProperties, messageText)
        Left(ParseError(InvalidRequest("Missing URL path"), message))
      }
    }

  private def retrieveRequest(
    requestBody: Array[Byte],
    requestContext: Context,
  ): Either[ParseError[Metadata], Request[Node]] =
    requestContext.method.filter(_ == HttpMethod.Get).map { _ =>
      // HTTP GET method - assemble request from URL query parameters
      val parameterNames = requestContext.parameters.map(_._1)
      val duplicateParameters = parameterNames.diff(parameterNames.distinct)
      if (duplicateParameters.nonEmpty) {
        Left(ParseError(
          InvalidRequest(s"Duplicate query parameters: ${duplicateParameters.mkString(", ")}"),
          protocol.Message((), requestBody),
        ))
      } else { Right(requestContext.parameters.map { case (name, value) => name -> encodeString(value) }.toMap) }
    }.getOrElse {
      // Other HTTP methods - deserialize request
      Try(decodeRequest(messageCodec.deserialize(requestBody))).fold(
        error => Left(
          ParseError(InvalidRequest("Malformed request", error), protocol.Message((), requestBody))
        ),
        request => Right(request),
      )
    }

  @nowarn("msg=used")
  override def createResponse(result: Try[Node], requestMetadata: Metadata): Try[protocol.Response[Node, Metadata]] = {
    // Create response
    val responseMessage = result.fold(
      error => {
        val responseError = error match {
          case WebRpcException(message, code, _) => ResponseError(message, code)
          case _ =>
            // Assemble error details
            val trace = error.trace
            val message = trace.mkString("\n")
            val errorType = mapException(error)
            ResponseError(message, Some(errorType.code))
        }
        Response[Node](None, Some(responseError)).message
      },
      resultValue => Response(Some(resultValue), None).message,
    )

    // Serialize response
    val messageText = () => Some(messageCodec.text(encodeResponse(responseMessage)))
    Try(messageCodec.serialize(encodeResponse(responseMessage))).recoverWith { case error =>
      Failure(InvalidResponse("Malformed response", error))
    }.map { messageBody =>
      val message = protocol.Message((), messageBody, responseMessage.properties, messageText)
      protocol.Response(result, message)
    }
  }

  @nowarn("msg=used")
  override def parseResponse(
    responseBody: Array[Byte],
    responseContext: Context,
  ): Either[ParseError[Metadata], protocol.Response[Node, Metadata]] =
    // Deserialize response
    Try(decodeResponse(messageCodec.deserialize(responseBody))).fold(
      error => Left(
        ParseError(InvalidResponse("Malformed response", error), protocol.Message((), responseBody))
      ),
      responseMessage => {
        // Validate response
        val messageText = () => Some(messageCodec.text(encodeResponse(responseMessage)))
        val message = protocol.Message((), responseBody, responseMessage.properties, messageText)
        Try(Response(responseMessage)).fold(
          error => Left(ParseError(InvalidResponse("Malformed response", error), message)),
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

  override def apiSchemas: Seq[ApiSchema[Node]] = {
    Seq(
      ApiSchema(
        RpcFunction(WebRpcProtocol.openApiFunction, Seq(), OpenApi.getClass.getSimpleName, None),
        functions => encodeOpenApi(openApi(functions)),
      )
    )
  }

  /**
   * Creates a copy of this protocol with specified message contex type.
   *
   * @tparam NewContext
   *   RPC message context type
   * @return
   *   JSON-RPC protocol
   */
  def context[NewContext <: HttpContext[?]]: WebRpcProtocol[Node, Codec, NewContext] =
    copy()

  /**
   * Creates a copy of this protocol with specified exception to Web-RPC error mapping.
   *
   * @param exceptionToError
   *   maps an exception classs to a corresponding Web-RPC error type
   * @return
   *   Web-RPC protocol
   */
  def mapException(exceptionToError: Throwable => ErrorType): WebRpcProtocol[Node, Codec, Context] =
    copy(mapException = exceptionToError)

  /**
   * Creates a copy of this protocol with specified Web-RPC error to exception mapping.
   *
   * @param errorToException
   *   maps a Web-RPC error to a corresponding exception
   * @return
   *   Web-RPC protocol
   */
  def mapError(errorToException: (String, Option[Int]) => Throwable): WebRpcProtocol[Node, Codec, Context] =
    copy(mapError = errorToException)

  /**
   * Creates a copy of this protocol with given OpenAPI description transformation.
   *
   * @param mapOpenApi
   *   transforms generated OpenAPI schema
   * @return
   *   Web-RPC protocol
   */
  def mapOpenApi(mapOpenApi: OpenApi => OpenApi): WebRpcProtocol[Node, Codec, Context] =
    copy(mapOpenApi = mapOpenApi)

  private def openApi(functions: Iterable[RpcFunction]): OpenApi = {
    val functionSchemas = functions.map { function =>
      function -> RpcSchema(requestSchema(function), resultSchema(function), errorSchema)
    }
    mapOpenApi(OpenApi(functionSchemas))
  }

  private def requestSchema(function: RpcFunction): Schema =
    Schema(
      Some(OpenApi.objectType),
      Some(function.name),
      Some(OpenApi.argumentsDescription),
      Option(Schema.parameters(function)).filter(_.nonEmpty),
      Option(Schema.requiredParameters(function).toList).filter(_.nonEmpty),
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
