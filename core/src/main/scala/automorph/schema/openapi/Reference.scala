package automorph.schema.openapi

trait Reference {
  def $ref: Option[String]
}
