package automorph.codec

import automorph.codec.WeePickleOpenRpc.{fromSchema, toSchema}
import automorph.schema.openapi.*
import automorph.schema.{OpenApi, Schema}
import com.rallyhealth.weepickle.v1.WeePickle.{From, FromTo, FromValue, To, ToValue, macroFromTo}

/** OpenAPI schema support for weePickle message codec plugin. */
private[automorph] object WeePickleOpenApi {

  @scala.annotation.nowarn("msg=never used")
  def fromTo: FromTo[OpenApi] = {
    implicit val schemaFrom: From[Schema] = FromValue.comap(fromSchema)
    implicit val schemaTo: To[Schema] = ToValue.map(toSchema)
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
}
