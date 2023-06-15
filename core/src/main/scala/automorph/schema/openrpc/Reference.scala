package automorph.schema.openrpc

trait Reference {
  def $ref: Option[String]
}
