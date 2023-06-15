package automorph.schema.openapi

final case class Server(
  url: String,
  description: Option[String] = None,
  variables: Option[Map[String, ServerVariable]] = None,
)
