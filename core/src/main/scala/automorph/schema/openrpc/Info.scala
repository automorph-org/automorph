package automorph.schema.openrpc

final case class Info(
  title: String,
  description: Option[String] = None,
  termsOfService: Option[String] = None,
  contact: Option[Contact] = None,
  license: Option[License] = None,
  version: String,
)
