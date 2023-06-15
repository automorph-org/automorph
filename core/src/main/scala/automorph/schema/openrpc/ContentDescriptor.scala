package automorph.schema.openrpc

final case class ContentDescriptor(
  name: String,
  summary: Option[String] = None,
  description: Option[String] = None,
  required: Option[Boolean] = None,
  schema: Schema,
  deprecated: Option[Boolean] = None,
  $ref: Option[String] = None,
) extends Reference
