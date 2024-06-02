package automorph.protocol.webrpc

import automorph.RpcException.{InvalidRequest, InvalidResponse}
import automorph.RpcFunction
import automorph.protocol.WebRpcProtocol
import automorph.protocol.webrpc.Message.Request
import automorph.schema.{OpenApi, Schema}
import automorph.schema.openapi.RpcSchema
import automorph.spi.{MessageCodec, protocol}
import automorph.spi.protocol.{ApiSchema, ParseError}
import automorph.transport.{HttpContext, HttpMethod}
import automorph.util.Extensions.ThrowableOps
import scala.util.{Failure, Success, Try}

/**
 * Web-RPC protocol core logic.
 *
 * @tparam Value
 *   message codec value representation type
 * @tparam Codec
 *   message codec plugin type
 * @tparam Context
 *   RPC message context type
 */
private[automorph] trait WebRpcBase[Value, Codec <: MessageCodec[Value], Context <: HttpContext[?]] {
  this: WebRpcProtocol[Value, Codec, Context] =>

  /** Web-RPC message metadata. */
  type Metadata = String
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
  private val functionSeparator = "^/+".r

  override def createRequest(
    function: String,
    arguments: Iterable[(String, Value)],
    respond: Boolean,
    context: Context,
    id: String,
  ): Try[protocol.Request[Value, Metadata, Context]] = {
    // Create request
    val request = arguments.toMap
    val requestProperties =
      Map("Type" -> MessageType.Call.toString, "Function" -> function, "Arguments" -> arguments.size.toString)

    // Serialize request
    val messageText = () => Some(messageCodec.text(encodeRequest(request)))
    Try(messageCodec.serialize(encodeRequest(request))).recoverWith { case error =>
      Failure(InvalidRequest("Malformed request", error))
    }.map { messageBody =>
      val message = protocol.Message(id, messageBody, requestProperties, messageText)
      val requestArguments = arguments.map(Right.apply[Value, (String, Value)]).toSeq
      val pathContext = context.path(s"${context.path.getOrElse("")}/$function").asInstanceOf[Context]
      protocol.Request(function, requestArguments, respond, pathContext, message, id)
    }
  }

  override def parseRequest(
    body: Array[Byte],
    context: Context,
    id: String,
  ): Either[ParseError[Metadata], protocol.Request[Value, Metadata, Context]] =
    retrieveRequest(body, context, id).flatMap { request =>
      // Validate request
      val messageText = () => Some(messageCodec.text(encodeRequest(request)))
      val requestProperties = Map("Type" -> MessageType.Call.toString, "Arguments" -> request.size.toString)
      context.path.map { path =>
        if (path.startsWith(pathPrefix) && path.length > pathPrefix.length) {
          val function = functionSeparator.replaceFirstIn(path.substring(pathPrefix.length, path.length), "")
          val message = protocol.Message(id, body, requestProperties ++ Seq("Function" -> function), messageText)
          val requestArguments = request.map(Right.apply[Value, (String, Value)]).toSeq
          Right(protocol.Request(function, requestArguments, respond = true, context, message, id))
        } else {
          val message = protocol.Message(id, body, requestProperties, messageText)
          Left(ParseError(InvalidRequest(s"Invalid URL path: $path"), message))
        }
      }.getOrElse {
        val message = protocol.Message(id, body, requestProperties, messageText)
        Left(ParseError(InvalidRequest("Missing URL path"), message))
      }
    }

  override def createResponse(result: Try[Value], requestMetadata: Metadata): Try[protocol.Response[Value, Metadata]] = {
    // Create response
    val responseMessage = result.fold(
      { error =>
        val responseError = error match {
          case WebRpcException(message, code, _) => ResponseError(message, code)
          case _ =>
            // Assemble error details
            val trace = error.trace
            val message = trace.mkString("\n")
            val errorType = mapException(error)
            ResponseError(message, Some(errorType.code))
        }
        Response[Value](None, Some(responseError)).message
      },
      resultValue => Response(Some(resultValue), None).message,
    )

    // Serialize response
    val messageText = () => Some(messageCodec.text(encodeResponse(responseMessage)))
    Try(messageCodec.serialize(encodeResponse(responseMessage))).recoverWith { case error =>
      Failure(InvalidResponse("Malformed response", error))
    }.map { messageBody =>
      val message = protocol.Message(requestMetadata, messageBody, responseMessage.properties, messageText)
      protocol.Response(result, message, requestMetadata)
    }
  }

  override def parseResponse(
    body: Array[Byte],
    context: Context,
    id: String,
  ): Either[ParseError[Metadata], protocol.Response[Value, Metadata]] =
    // Deserialize response
    Try(decodeResponse(messageCodec.deserialize(body))).fold(
      error => Left(ParseError(InvalidResponse("Malformed response", error), protocol.Message(id, body))),
      { responseMessage =>
        // Validate response
        val messageText = () => Some(messageCodec.text(encodeResponse(responseMessage)))
        val message = protocol.Message(id, body, responseMessage.properties, messageText)
        Try(Response(responseMessage)).fold(
          error => Left(ParseError(InvalidResponse("Malformed response", error), message)),
          response =>
            // Extract result or error
            response.error.fold(response.result match {
              case None => Left(ParseError(InvalidResponse("Invalid result", None.orNull), message))
              case Some(result) => Right(protocol.Response(Success(result), message, id))
            }) { error =>
              val exception = mapError(error.message, error.code)
              Right(protocol.Response(Failure(exception), message, id))
            },
        )
      },
    )

  override def apiSchemas: Seq[ApiSchema[Value]] =
    Seq(
      ApiSchema(
        RpcFunction(WebRpcProtocol.openApiFunction, Seq(), OpenApi.getClass.getSimpleName, None),
        functions => encodeOpenApi(openApi(functions)),
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
  def context[NewContext <: HttpContext[?]]: WebRpcProtocol[Value, Codec, NewContext] =
    copy()

  /**
   * Creates a copy of this protocol with specified exception to Web-RPC error mapping.
   *
   * @param exceptionToError
   *   maps an exception classs to a corresponding Web-RPC error type
   * @return
   *   Web-RPC protocol
   */
  def mapException(exceptionToError: Throwable => ErrorType): WebRpcProtocol[Value, Codec, Context] =
    copy(mapException = exceptionToError)

  /**
   * Creates a copy of this protocol with specified Web-RPC error to exception mapping.
   *
   * @param errorToException
   *   maps a Web-RPC error to a corresponding exception
   * @return
   *   Web-RPC protocol
   */
  def mapError(errorToException: (String, Option[Int]) => Throwable): WebRpcProtocol[Value, Codec, Context] =
    copy(mapError = errorToException)

  /**
   * Creates a copy of this protocol with given OpenAPI description transformation.
   *
   * @param mapOpenApi
   *   transforms generated OpenAPI schema
   * @return
   *   Web-RPC protocol
   */
  def mapOpenApi(mapOpenApi: OpenApi => OpenApi): WebRpcProtocol[Value, Codec, Context] =
    copy(mapOpenApi = mapOpenApi)

  private def retrieveRequest(
    body: Array[Byte],
    context: Context,
    id: String,
  ): Either[ParseError[Metadata], Request[Value]] =
    context.method.filter(_ == HttpMethod.Get).map { _ =>
      // HTTP GET method - assemble request from URL query parameters
      val parameterNames = context.parameters.map(_._1)
      val duplicateParameters = parameterNames.diff(parameterNames.distinct)
      if (duplicateParameters.nonEmpty) {
        Left(ParseError(
          InvalidRequest(s"Duplicate query parameters: ${duplicateParameters.mkString(", ")}"),
          protocol.Message(id, body),
        ))
      } else { Right(context.parameters.map { case (name, value) => name -> encodeString(value) }.toMap) }
    }.getOrElse {
      // Other HTTP methods - deserialize request
      Try(decodeRequest(messageCodec.deserialize(body))).fold(
        error => Left(ParseError(InvalidRequest("Malformed request", error), protocol.Message(id, body))),
        request => Right(request),
      )
    }

  private def openApi(functions: Iterable[RpcFunction]): OpenApi = {
    val functionSchemas = functions.map { function =>
      function -> RpcSchema(requestSchema(function), resultSchema(function), errorSchema)
    }
    mapOpenApi(OpenApi.from(functionSchemas))
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
