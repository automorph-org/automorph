package automorph.codec.messagepack

import automorph.schema.OpenApi
import automorph.schema.openapi.*
import com.rallyhealth.weepack.v1.Msg.{FromMsgValue, ToMsgValue}
import com.rallyhealth.weepack.v1.{Arr, Msg, Obj, Str}
import com.rallyhealth.weepickle.v1.WeePickle.{From, FromTo, To, macroFromTo}
import com.rallyhealth.weepickle.v1.core.Abort
import scala.collection.mutable

/** OpenAPI schema support for weePickle message codec plugin. */
private[automorph] object WeePickleOpenApi {

  @scala.annotation.nowarn("msg=never used")
  def fromTo: FromTo[OpenApi] = {
    implicit val schemaFrom: From[Schema] = FromMsgValue.comap(fromSchema)
    implicit val schemaTo: To[Schema] = ToMsgValue.map(toSchema)
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

  private def fromSchema(schema: Schema): Msg =
    Obj(mutable.LinkedHashMap.newBuilder[Msg, Msg].addAll(
      Seq(
        schema.`type`.map(Str("type") -> Str(_)),
        schema.title.map(Str("title") -> Str(_)),
        schema.description.map(Str("description") -> Str(_)),
        schema.properties.map(v =>
          Str("properties") -> Obj(mutable.LinkedHashMap.newBuilder[Msg, Msg].addAll(v.map {
            case (key, value) =>
              Str(key) -> fromSchema(value)
          }).result())
        ),
        schema.required.map(v => Str("required") -> Arr(v.map(Str.apply)*)),
        schema.default.map(Str("default") -> Str(_)),
        schema.allOf.map(v => Str("allOf") -> Arr(v.map(fromSchema)*)),
        schema.$ref.map(Str("$ref") -> Str(_)),
      ).flatten
    ).result())

  private def toSchema(node: Msg): Schema =
    node match {
      case Obj(fields) => Schema(
          `type` = fields.get(Str("type")).map(_.str),
          title = fields.get(Str("title")).map(_.str),
          description = fields.get(Str("description")).map(_.str),
          properties = fields.get(Str("properties")).map(_.obj.map { case (key, value) =>
            key.str -> toSchema(value)
          }.toMap),
          required = fields.get(Str("required")).map(_.arr.map(_.str).toList),
          default = fields.get(Str("default")).map(_.str),
          allOf = fields.get(Str("allOf")).map(_.arr.map(toSchema).toList),
          $ref = fields.get(Str("$ref")).map(_.str),
        )
      case _ => throw new Abort("Invalid OpenAPI object")
    }
}
