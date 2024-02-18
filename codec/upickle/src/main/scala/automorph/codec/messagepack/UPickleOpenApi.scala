package automorph.codec.messagepack

import automorph.codec.messagepack.UPickleOpenRpc.{fromSchema, toSchema}
import automorph.schema.openapi.*
import automorph.schema.{OpenApi, Schema}
import upack.Msg

/** JSON-RPC protocol support for Circe message codec plugin using JSON format. */
private[automorph] object UPickleOpenApi {

  @scala.annotation.nowarn("msg=never used")
  def readWriter[Config <: UPickleMessagePackConfig](config: Config): config.ReadWriter[OpenApi] = {
    import config.*

    implicit val schemaRw: config.ReadWriter[Schema] = readwriter[Msg].bimap[Schema](fromSchema, toSchema)
    implicit val authFlowRw: config.ReadWriter[OAuthFlow] = config.macroRW
    implicit val contactRw: config.ReadWriter[Contact] = config.macroRW
    implicit val externalDocumentationRw: config.ReadWriter[ExternalDocumentation] = config.macroRW
    implicit val exampleRw: config.ReadWriter[Example] = config.macroRW
    implicit val headerReferenceRw: config.ReadWriter[HeaderReference] = config.macroRW
    implicit val licenseRw: config.ReadWriter[License] = config.macroRW
    implicit val pathItemReferenceRw: config.ReadWriter[PathItemReference] = config.macroRW
    implicit val serverVariableRw: config.ReadWriter[ServerVariable] = config.macroRW
    implicit val authFlowsRw: config.ReadWriter[OAuthFlows] = config.macroRW
    implicit val infoRw: config.ReadWriter[Info] = config.macroRW
    implicit val securitySchemeRw: config.ReadWriter[SecurityScheme] = config.macroRW
    implicit val serverRw: config.ReadWriter[Server] = config.macroRW
    implicit val tagRw: config.ReadWriter[Tag] = config.macroRW
    implicit val encodingRw: config.ReadWriter[Encoding] = config.macroRW
    implicit val mediaTypeRw: config.ReadWriter[MediaType] = config.macroRW
    implicit val headerRw: config.ReadWriter[Header] = config.macroRW
    implicit val linkRw: config.ReadWriter[Link] = config.macroRW
    implicit val parameterRw: config.ReadWriter[Parameter] = config.macroRW
    implicit val requestBodyRw: config.ReadWriter[RequestBody] = config.macroRW
    implicit val responseRw: config.ReadWriter[Response] = config.macroRW
    implicit val operationRw: config.ReadWriter[Operation] = config.macroRW
    implicit val pathItemRw: config.ReadWriter[PathItem] = config.macroRW
    implicit val componentsRw: config.ReadWriter[Components] = config.macroRW

    config.macroRW[OpenApi]
  }
}
