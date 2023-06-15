package automorph.schema.openapi

final case class PathItem(
  $ref: Option[String] = None,
  summary: Option[String] = None,
  description: Option[String] = None,
  get: Option[Operation] = None,
  put: Option[Operation] = None,
  post: Option[Operation] = None,
  delete: Option[Operation] = None,
  options: Option[Operation] = None,
  head: Option[Operation] = None,
  patch: Option[Operation] = None,
  trace: Option[Operation] = None,
  servers: Option[List[Server]] = None,
  parameters: Option[List[Parameter]] = None,
)
