package automorph.codec

import automorph.schema.OpenApi
import automorph.schema.openapi.*
import com.rallyhealth.weejson.v1.{Arr, Obj, Str, Value}
import com.rallyhealth.weepickle.v1.WeePickle.{FromTo, macroFromTo}
import com.rallyhealth.weepickle.v1.core.Abort
import scala.annotation.nowarn

/** OpenAPI schema support for weePickle message codec plugin. */
private[automorph] object WeepickleOpenApi {

  @nowarn("msg=used")
  def fromTo: FromTo[OpenApi] = {
    implicit val schemaFromTo: FromTo[Schema] = macroFromTo[Value].bimap(fromSchema, toSchema)
    implicit val authFlowFromTo: FromTo[OAuthFlow] = macroFromTo
    implicit val contactFromTo: FromTo[Contact] = macroFromTo
    implicit val externalDocumentationFromTo: FromTo[ExternalDocumentation] = macroFromTo
    implicit val exampleFromTo: FromTo[Example] = macroFromTo
    implicit val headerReferenceFromTo: FromTo[HeaderReference] = macroFromTo
    implicit val licenseFromTo: FromTo[License] = macroFromTo
    implicit val pathItemReferenceFromTo: FromTo[PathItemReference] = macroFromTo
    implicit val serverVariableFromTo: FromTo[ServerVariable] = macroFromTo
    implicit val authFlowsFromTo: FromTo[OAuthFlows] = macroFromTo
    implicit val infoFromTo: FromTo[Info] = macroFromTo
    implicit val securitySchemeFromTo: FromTo[SecurityScheme] = macroFromTo
    implicit val serverFromTo: FromTo[Server] = macroFromTo
    implicit val tagFromTo: FromTo[Tag] = macroFromTo
    implicit val encodingFromTo: FromTo[Encoding] = macroFromTo
    implicit val mediaTypeFromTo: FromTo[MediaType] = macroFromTo
    implicit val headerFromTo: FromTo[Header] = macroFromTo
    implicit val linkFromTo: FromTo[Link] = macroFromTo
    implicit val parameterFromTo: FromTo[Parameter] = macroFromTo
    implicit val requestBodyFromTo: FromTo[RequestBody] = macroFromTo
    implicit val responseFromTo: FromTo[Response] = macroFromTo
    implicit val operationFromTo: FromTo[Operation] = macroFromTo
    implicit val pathItemFromTo: FromTo[PathItem] = macroFromTo
    implicit val componentsFromTo: FromTo[Components] = macroFromTo
    macroFromTo[OpenApi]
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
      case _ => throw new Abort("Invalid OpenAPI object")
    }
}
