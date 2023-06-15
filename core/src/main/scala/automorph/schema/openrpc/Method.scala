package automorph.schema.openrpc

final case class Method(
  name: String,
  tags: Option[List[Tag]] = None,
  summary: Option[String] = None,
  description: Option[String] = None,
  externalDocs: Option[ExternalDocumentation] = None,
  params: List[ContentDescriptor],
  result: Option[ContentDescriptor],
  deprecated: Option[Boolean] = None,
  servers: Option[List[Server]] = None,
  errors: Option[List[Error]] = None,
  links: Option[List[Link]] = None,
  paramStructure: Option[String] = None,
  examples: Option[List[ExamplePairing]] = None,
  $ref: Option[String] = None,
) extends Reference
