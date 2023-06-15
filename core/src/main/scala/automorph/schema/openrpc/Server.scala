package automorph.schema.openrpc

final case class Server(
  name: String,
  url: String,
  summary: Option[String] = None,
  description: Option[String] = None,
  variables: Option[Map[String, ServerVariable]] = None,
)
