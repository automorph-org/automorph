package automorph.schema.openrpc

final case class Tag(
  name: String,
  summary: Option[String] = None,
  description: Option[String] = None,
  externalDocs: Option[ExternalDocumentation] = None,
  $ref: Option[String] = None,
) extends Reference
