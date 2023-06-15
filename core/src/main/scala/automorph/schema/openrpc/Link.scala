package automorph.schema.openrpc

final case class Link(
  name: String,
  summary: Option[String] = None,
  description: Option[String] = None,
  method: Option[String] = None,
  params: Option[Map[String, String]] = None,
  server: Option[Server] = None,
  $ref: Option[String] = None,
) extends Reference
