package automorph.schema.openapi

final case class RequestBody(
  description: Option[String] = None,
  content: Map[String, MediaType],
  required: Option[Boolean] = None,
  $ref: Option[String] = None,
) extends Reference
