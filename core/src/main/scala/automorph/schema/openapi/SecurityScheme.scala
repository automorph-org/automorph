package automorph.schema.openapi

final case class SecurityScheme(
  `type`: String,
  description: Option[String] = None,
  name: Option[String] = None,
  in: Option[String] = None,
  scheme: Option[String] = None,
  bearerFormat: Option[String] = None,
  flows: Option[OAuthFlows] = None,
  openIdConnectUrl: Option[String] = None,
  $ref: Option[String] = None,
) extends Reference
