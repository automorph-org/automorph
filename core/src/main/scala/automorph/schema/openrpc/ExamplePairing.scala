package automorph.schema.openrpc

final case class ExamplePairing(
  name: String,
  summary: Option[String] = None,
  description: Option[String] = None,
  params: List[Example] = List(),
  result: Option[Example] = None,
)
