package automorph.schema.openrpc

final case class Example(
  name: Option[String] = None,
  summary: Option[String] = None,
  description: Option[String] = None,
  value: Option[String],
  externalValue: Option[String] = None,
  $ref: Option[String] = None,
) extends Reference
