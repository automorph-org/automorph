package automorph.codec.json

import automorph.schema.openapi.*
import automorph.schema.{OpenApi, Schema}
import play.api.libs.json.{Json, Reads, Writes}

/** OpenAPI schema support for Play JSON message codec plugin. */
private[automorph] object PlayJsonOpenApi {

  val reads: Reads[OpenApi] = {
    implicit val schemaFrom: Reads[Schema] = Json.reads
    implicit val authFlowReads: Reads[OAuthFlow] = Json.reads
    implicit val contactReads: Reads[Contact] = Json.reads
    implicit val externalDocumentationReads: Reads[ExternalDocumentation] = Json.reads
    implicit val exampleReads: Reads[Example] = Json.reads
    implicit val headerReferenceReads: Reads[HeaderReference] = Json.reads
    implicit val licenseReads: Reads[License] = Json.reads
    implicit val pathItemReferenceReads: Reads[PathItemReference] = Json.reads
    implicit val serverVariableReads: Reads[ServerVariable] = Json.reads
    implicit val authFlowsReads: Reads[OAuthFlows] = Json.reads
    implicit val infoReads: Reads[Info] = Json.reads
    implicit val securitySchemeReads: Reads[SecurityScheme] = Json.reads
    implicit val serverReads: Reads[Server] = Json.reads
    implicit val tagReads: Reads[Tag] = Json.reads
    implicit val encodingReads: Reads[Encoding] = Json.reads
    implicit val mediaTypeReads: Reads[MediaType] = Json.reads
    implicit val headerReads: Reads[Header] = Json.reads
    implicit val linkReads: Reads[Link] = Json.reads
    implicit val parameterReads: Reads[Parameter] = Json.reads
    implicit val requestBodyReads: Reads[RequestBody] = Json.reads
    implicit val responseReads: Reads[Response] = Json.reads
    implicit val operationReads: Reads[Operation] = Json.reads
    implicit val pathItemReads: Reads[PathItem] = Json.reads
    implicit val componentsReads: Reads[Components] = Json.reads
    Json.reads[OpenApi]
  }

  val writes: Writes[OpenApi] = {
    implicit val schemaFrom: Writes[Schema] = Json.writes
    implicit val authFlowWrites: Writes[OAuthFlow] = Json.writes
    implicit val contactWrites: Writes[Contact] = Json.writes
    implicit val externalDocumentationWrites: Writes[ExternalDocumentation] = Json.writes
    implicit val exampleWrites: Writes[Example] = Json.writes
    implicit val headerReferenceWrites: Writes[HeaderReference] = Json.writes
    implicit val licenseWrites: Writes[License] = Json.writes
    implicit val pathItemReferenceWrites: Writes[PathItemReference] = Json.writes
    implicit val serverVariableWrites: Writes[ServerVariable] = Json.writes
    implicit val authFlowsWrites: Writes[OAuthFlows] = Json.writes
    implicit val infoWrites: Writes[Info] = Json.writes
    implicit val securitySchemeWrites: Writes[SecurityScheme] = Json.writes
    implicit val serverWrites: Writes[Server] = Json.writes
    implicit val tagWrites: Writes[Tag] = Json.writes
    implicit val encodingWrites: Writes[Encoding] = Json.writes
    implicit val mediaTypeWrites: Writes[MediaType] = Json.writes
    implicit val headerWrites: Writes[Header] = Json.writes
    implicit val linkWrites: Writes[Link] = Json.writes
    implicit val parameterWrites: Writes[Parameter] = Json.writes
    implicit val requestBodyWrites: Writes[RequestBody] = Json.writes
    implicit val responseWrites: Writes[Response] = Json.writes
    implicit val operationWrites: Writes[Operation] = Json.writes
    implicit val pathItemWrites: Writes[PathItem] = Json.writes
    implicit val componentsWrites: Writes[Components] = Json.writes
    Json.writes[OpenApi]
  }
}