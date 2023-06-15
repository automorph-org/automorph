package automorph.schema.openapi

final case class Example(
  summary: Option[String] = None,
  description: Option[String] = None,
  value: Option[String],
  externalValue: Option[String] = None,
  $ref: Option[String] = None,
) extends Reference
