package automorph.schema.openapi

final case class Response(
  description: String,
  headers: Option[Map[String, Header]] = None,
  content: Option[Map[String, MediaType]] = None,
  links: Option[Map[String, Link]] = None,
  $ref: Option[String] = None,
) extends Reference
