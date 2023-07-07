package automorph.codec.json

import automorph.schema.OpenApi
import automorph.schema.openapi.*
import ujson.{Arr, Obj, Str, Value}
import upickle.core.Abort

/** JSON-RPC protocol support for Circe message codec plugin using JSON format. */
private[automorph] object UpickleOpenApi {

  def readWriter[Custom <: UpickleJsonCustom](custom: Custom): custom.ReadWriter[OpenApi] = {
    import custom.*

    implicit val schemaRw: custom.ReadWriter[Schema] = readwriter[Value].bimap[Schema](fromSchema, toSchema)
    implicit val authFlowRw: custom.ReadWriter[OAuthFlow] = custom.macroRW
    implicit val contactRw: custom.ReadWriter[Contact] = custom.macroRW
    implicit val externalDocumentationRw: custom.ReadWriter[ExternalDocumentation] = custom.macroRW
    implicit val exampleRw: custom.ReadWriter[Example] = custom.macroRW
    implicit val headerReferenceRw: custom.ReadWriter[HeaderReference] = custom.macroRW
    implicit val licenseRw: custom.ReadWriter[License] = custom.macroRW
    implicit val pathItemReferenceRw: custom.ReadWriter[PathItemReference] = custom.macroRW
    implicit val serverVariableRw: custom.ReadWriter[ServerVariable] = custom.macroRW
    implicit val authFlowsRw: custom.ReadWriter[OAuthFlows] = custom.macroRW
    implicit val infoRw: custom.ReadWriter[Info] = custom.macroRW
    implicit val securitySchemeRw: custom.ReadWriter[SecurityScheme] = custom.macroRW
    implicit val serverRw: custom.ReadWriter[Server] = custom.macroRW
    implicit val tagRw: custom.ReadWriter[Tag] = custom.macroRW
    implicit val encodingRw: custom.ReadWriter[Encoding] = custom.macroRW
    implicit val mediaTypeRw: custom.ReadWriter[MediaType] = custom.macroRW
    implicit val headerRw: custom.ReadWriter[Header] = custom.macroRW
    implicit val linkRw: custom.ReadWriter[Link] = custom.macroRW
    implicit val parameterRw: custom.ReadWriter[Parameter] = custom.macroRW
    implicit val requestBodyRw: custom.ReadWriter[RequestBody] = custom.macroRW
    implicit val responseRw: custom.ReadWriter[Response] = custom.macroRW
    implicit val operationRw: custom.ReadWriter[Operation] = custom.macroRW
    implicit val pathItemRw: custom.ReadWriter[PathItem] = custom.macroRW
    implicit val componentsRw: custom.ReadWriter[Components] = custom.macroRW

    custom.macroRW[OpenApi]
  }

  private def fromSchema(schema: Schema): Value =
    Obj.from(
      Seq(
        schema.`type`.map("type" -> Str(_)),
        schema.title.map("title" -> Str(_)),
        schema.description.map("description" -> Str(_)),
        schema.properties.map(v => "properties" -> Obj.from(v.view.mapValues(fromSchema).toSeq)),
        schema.required.map(v => "required" -> Arr.from(v.map(Str.apply))),
        schema.default.map("default" -> Str(_)),
        schema.allOf.map(v => "allOf" -> Arr.from(v.map(fromSchema))),
        schema.$ref.map("$ref" -> Str(_)),
      ).flatten
    )

  private def toSchema(node: Value): Schema =
    node match {
      case Obj(fields) => Schema(
          `type` = fields.get("type").map(_.str),
          title = fields.get("title").map(_.str),
          description = fields.get("description").map(_.str),
          properties = fields.get("properties").map(_.obj.view.mapValues(toSchema).toMap),
          required = fields.get("required").map(_.arr.map(_.str).toList),
          default = fields.get("default").map(_.str),
          allOf = fields.get("allOf").map(_.arr.map(toSchema).toList),
          $ref = fields.get("$ref").map(_.str),
        )
      case _ => throw Abort(s"Invalid OpenAPI object")
    }
}
