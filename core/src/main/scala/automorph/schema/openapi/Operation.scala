package automorph.schema.openapi

import automorph.schema.openapi.Operation.{Callback, Responses, SecurityRequirement}

final case class Operation(
  tags: Option[List[String]] = None,
  summary: Option[String] = None,
  description: Option[String] = None,
  externalDocs: Option[ExternalDocumentation] = None,
  operationId: Option[String] = None,
  parameters: Option[List[Parameter]] = None,
  requestBody: Option[RequestBody] = None,
  responses: Responses,
  callbacks: Option[Map[String, Callback]] = None,
  deprecated: Option[Boolean] = None,
  security: Option[List[SecurityRequirement]] = None,
  servers: Option[List[Server]] = None,
)

case object Operation {

  type Responses = Map[String, Response]
  type Callback = Map[String, PathItemReference]
  type SecurityRequirement = Map[String, List[String]]
}
