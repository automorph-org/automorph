package automorph.schema.openrpc

final case class ExamplePairing(
  name: String,
  summary: Option[String] = None,
  description: Option[String] = None,
  params: Option[List[Example]] = None,
  result: Option[Example] = None,
)
