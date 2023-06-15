package automorph.schema.openapi

final case class Tag(name: String, description: Option[String] = None, externalDocs: Option[ExternalDocumentation] = None)
