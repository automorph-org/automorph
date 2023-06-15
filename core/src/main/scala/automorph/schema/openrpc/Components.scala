package automorph.schema.openrpc

case class Components(
  contentDescriptors: Option[Map[String, ContentDescriptor]] = None,
  schemas: Option[Map[String, Schema]] = None,
  examples: Option[Map[String, Example]] = None,
  links: Option[Map[String, Link]] = None,
  error: Option[Map[String, Error]] = None,
  examplePairingObjects: Option[Map[String, ExamplePairing]] = None,
  tags: Option[Map[String, Tag]] = None,
)
