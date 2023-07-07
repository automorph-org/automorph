package automorph.codec.json

import automorph.schema.OpenApi
import automorph.schema.openapi.*
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.{ACursor, Decoder, DecodingFailure, Encoder, HCursor, Json}

/** JSON-RPC protocol support for Circe message codec plugin using JSON format. */
private[automorph] object CirceOpenApi {

  def openApiEncoder: Encoder[OpenApi] = {
    implicit val schemaEncoder: Encoder[Schema] = new Encoder[Schema] {

      override def apply(schema: Schema): Json = {
        val fields = Seq(
          schema.`type`.map(v => "type" -> Json.fromString(v)),
          schema.title.map(v => "title" -> Json.fromString(v)),
          schema.description.map(v => "description" -> Json.fromString(v)),
          schema.properties.map(v => "properties" -> Json.obj(v.view.mapValues(apply).toSeq*)),
          schema.required.map(v => "required" -> Json.arr(v.map(Json.fromString)*)),
          schema.default.map(v => "default" -> Json.fromString(v)),
          schema.allOf.map(v => "allOf" -> Json.arr(v.map(apply)*)),
          schema.$ref.map(v => "$ref" -> Json.fromString(v)),
        ).flatten
        Json.obj(fields*)
      }
    }
    implicit val oauthFlowEncoder: Encoder[OAuthFlow] = deriveEncoder[OAuthFlow]
    implicit val contactEncoder: Encoder[Contact] = deriveEncoder[Contact]
    implicit val externalDocumentationEncoder: Encoder[ExternalDocumentation] = deriveEncoder[ExternalDocumentation]
    implicit val exampleEncoder: Encoder[Example] = deriveEncoder[Example]
    implicit val headerReferenceEncoder: Encoder[HeaderReference] = deriveEncoder[HeaderReference]
    implicit val licenseEncoder: Encoder[License] = deriveEncoder[License]
    implicit val pathItemReferenceEncoder: Encoder[PathItemReference] = deriveEncoder[PathItemReference]
    implicit val serverVariableEncoder: Encoder[ServerVariable] = deriveEncoder[ServerVariable]
    implicit val oauthFlowsEncoder: Encoder[OAuthFlows] = deriveEncoder[OAuthFlows]
    implicit val infoEncoder: Encoder[Info] = deriveEncoder[Info]
    implicit val securitySchemeEncoder: Encoder[SecurityScheme] = deriveEncoder[SecurityScheme]
    implicit val serverEncoder: Encoder[Server] = deriveEncoder[Server]
    implicit val tagEncoder: Encoder[Tag] = deriveEncoder[Tag]
    implicit val encodingEncoder: Encoder[Encoding] = deriveEncoder[Encoding]
    implicit val mediaTypeEncoder: Encoder[MediaType] = deriveEncoder[MediaType]
    implicit val headerEncoder: Encoder[Header] = deriveEncoder[Header]
    implicit val linkEncoder: Encoder[Link] = deriveEncoder[Link]
    implicit val parameterEncoder: Encoder[Parameter] = deriveEncoder[Parameter]
    implicit val requestBodyEncoder: Encoder[RequestBody] = deriveEncoder[RequestBody]
    implicit val responseEncoder: Encoder[Response] = deriveEncoder[Response]
    implicit val operationEncoder: Encoder[Operation] = deriveEncoder[Operation]
    implicit val pathItemEncoder: Encoder[PathItem] = deriveEncoder[PathItem]
    implicit val componentsEncoder: Encoder[Components] = deriveEncoder[Components]

    deriveEncoder[OpenApi]
  }

  def openApiDecoder: Decoder[OpenApi] = {
    implicit val schemaDecoder: Decoder[Schema] = new Decoder[Schema] {

      private val propertiesField = "properties"
      private val allOfField = "allOf"

      override def apply(c: HCursor): Decoder.Result[Schema] =
        decode(c)

      private def decode(c: ACursor): Decoder.Result[Schema] =
        c.keys.map(_.toSet).map { keys =>
          for {
            `type` <- field[String](c, keys, "type")
            title <- field[String](c, keys, "title")
            description <- field[String](c, keys, "description")
            properties <- Option.when(keys.contains(propertiesField)) {
              val jsonObject = c.downField(propertiesField)
              val objectFields = jsonObject.keys.getOrElse(Seq())
              objectFields.foldLeft(Right(Map[String, Schema]()).withLeft[DecodingFailure]) { case (result, key) =>
                result.flatMap(schemas => decode(jsonObject.downField(key)).map(schema => schemas + (key -> schema)))
              }.map(Some.apply)
            }.getOrElse(Right(None))
            required <- field[List[String]](c, keys, "required")
            default <- field[String](c, keys, "default")
            allOf <- Option.when(keys.contains(allOfField)) {
              val jsonArray = c.downField(allOfField)
              val arrayIndices = jsonArray.values.map(_.toSeq).getOrElse(Seq()).indices
              arrayIndices.foldLeft(Right(List[Schema]()).withLeft[DecodingFailure]) { case (result, index) =>
                result.flatMap(schemas => decode(jsonArray.downN(index)).map(schemas :+ _))
              }.map(Some.apply)
            }.getOrElse(Right(None))
            $ref <- field[String](c, keys, "$ref")
          } yield Schema(
            `type` = `type`,
            title = title,
            description = description,
            properties = properties,
            required = required,
            default = default,
            allOf = allOf,
            $ref = $ref,
          )
        }.getOrElse(Left(DecodingFailure("Not a JSON object", c.history)))

      private def field[T](c: ACursor, keys: Set[String], name: String)(implicit
        decoder: Decoder[Option[T]]
      ): Decoder.Result[Option[T]] =
        if (keys.contains(name)) { c.downField(name).as[Option[T]] }
        else { Right(None) }
    }
    implicit val oauthFlowDecoder: Decoder[OAuthFlow] = deriveDecoder[OAuthFlow]
    implicit val contactDecoder: Decoder[Contact] = deriveDecoder[Contact]
    implicit val externalDocumentationDecoder: Decoder[ExternalDocumentation] = deriveDecoder[ExternalDocumentation]
    implicit val exampleDecoder: Decoder[Example] = deriveDecoder[Example]
    implicit val headerReferenceDecoder: Decoder[HeaderReference] = deriveDecoder[HeaderReference]
    implicit val licenseDecoder: Decoder[License] = deriveDecoder[License]
    implicit val pathItemReferenceDecoder: Decoder[PathItemReference] = deriveDecoder[PathItemReference]
    implicit val serverVariableDecoder: Decoder[ServerVariable] = deriveDecoder[ServerVariable]
    implicit val oauthFlowsDecoder: Decoder[OAuthFlows] = deriveDecoder[OAuthFlows]
    implicit val infoDecoder: Decoder[Info] = deriveDecoder[Info]
    implicit val securitySchemeDecoder: Decoder[SecurityScheme] = deriveDecoder[SecurityScheme]
    implicit val serverDecoder: Decoder[Server] = deriveDecoder[Server]
    implicit val tagDecoder: Decoder[Tag] = deriveDecoder[Tag]
    implicit val encodingDecoder: Decoder[Encoding] = deriveDecoder[Encoding]
    implicit val mediaTypeDecoder: Decoder[MediaType] = deriveDecoder[MediaType]
    implicit val headerDecoder: Decoder[Header] = deriveDecoder[Header]
    implicit val linkDecoder: Decoder[Link] = deriveDecoder[Link]
    implicit val parameterDecoder: Decoder[Parameter] = deriveDecoder[Parameter]
    implicit val requestBodyDecoder: Decoder[RequestBody] = deriveDecoder[RequestBody]
    implicit val responseDecoder: Decoder[Response] = deriveDecoder[Response]
    implicit val operationDecoder: Decoder[Operation] = deriveDecoder[Operation]
    implicit val pathItemDecoder: Decoder[PathItem] = deriveDecoder[PathItem]
    implicit val componentsDecoder: Decoder[Components] = deriveDecoder[Components]

    deriveDecoder[OpenApi]
  }
}
