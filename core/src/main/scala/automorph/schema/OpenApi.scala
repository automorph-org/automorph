package automorph.schema

import automorph.RpcFunction
import automorph.schema.OpenApi.{Components, Paths}
import automorph.schema.openapi.Operation.SecurityRequirement
import automorph.schema.openapi.{
  ExternalDocumentation, Info, MediaType, Operation, PathItem, RequestBody, Response, RpcSchema, Schema, Server, Tag,
}

/**
 * OpenAPI API schema.
 *
 * @see
 *   [[https://github.com/OAI/OpenAPI-Specification OpenAPI schema]]
 */
final case class OpenApi(
  openapi: String = "3.1.0",
  info: Info,
  jsonSchemaDialect: Option[String] = None,
  servers: Option[List[Server]] = None,
  paths: Option[Paths] = None,
  webhooks: Option[Map[String, PathItem]] = None,
  components: Option[Components] = None,
  security: Option[List[SecurityRequirement]] = None,
  tags: Option[List[Tag]] = None,
  externalDocs: Option[ExternalDocumentation] = None,
)

case object OpenApi {

  type Paths = Map[String, PathItem]
  type Components = Map[String, Schema]

  /** Result value name. */
  val resultName = "result"

  private[automorph] val requestTitle = "Request"
  private[automorph] val resultTitle = "Result"
  private[automorph] val errorTitle = "Error"
  private[automorph] val objectType = "object"
  private[automorph] val argumentsDescription = "Function argument values by name"
  private val defaultTitle = ""
  private val defaultVersion = ""
  private val contentType = "application/json"
  private val httpStatusCodeOk = 200.toString
  private val scaladocMarkup = "^[/* ]*$".r

  /**
   * Generates OpenAPI schema for given RPC functions.
   *
   * @param functionSchemas
   *   RPC function schemas
   * @return
   *   OpenAPI schema
   */
  def apply(functionSchemas: Iterable[(RpcFunction, RpcSchema)]): OpenApi = {
    val paths = functionSchemas.map { case (function, schema) =>
      // Request
      val requestMediaType = MediaType(schema = Some(schema.request))
      val resultMediaType = MediaType(schema = Some(schema.result))
      val errorMediaType = MediaType(schema = Some(schema.error))
      val requestBody = RequestBody(content = Map(contentType -> requestMediaType), required = Some(true))
      val responses = Map(
        // Error response
        "default" -> Response(
          description = "Failed function call error details",
          content = Some(Map(contentType -> errorMediaType)),
        ),
        // Result response
        httpStatusCodeOk -> Response(
          description = "Succesful function call result value",
          content = Some(Map(contentType -> resultMediaType)),
        ),
      )

      // Path
      val operation = Operation(requestBody = Some(requestBody), responses = responses)
      val summary = function.documentation.flatMap(_.split('\n').find {
        case scaladocMarkup(_*) => true
        case _ => false
      }.map(_.trim))
      val pathItem = PathItem(post = Some(operation), summary = summary, description = function.documentation)
      function.name -> pathItem
    }.toMap
    val info = Info(title = defaultTitle, version = defaultVersion)
    OpenApi(info = info, paths = Some(paths))
  }
}
