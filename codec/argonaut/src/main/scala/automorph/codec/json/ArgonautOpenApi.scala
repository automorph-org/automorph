package automorph.codec.json

import argonaut.Argonaut.{jArray, jObjectFields, jString}
import argonaut.{Argonaut, CodecJson, DecodeJson, DecodeResult, HCursor, Json}
import automorph.schema.OpenApi
import automorph.schema.openapi.*

/** JSON-RPC protocol support for uPickle message codec plugin using JSON format. */
private[automorph] object ArgonautOpenApi {

  private val propertiesField = "properties"
  private val allOfField = "allOf"

  def openApiCodecJson: CodecJson[OpenApi] = {
    implicit val schemaCodecJson: CodecJson[Schema] = CodecJson(fromSchema, toSchema)
    implicit val contactCodecJson: CodecJson[OAuthFlow] = Argonaut.codec4(
      OAuthFlow.apply,
      (v: OAuthFlow) => (v.authorizationUrl, v.tokenUrl, v.refreshUrl, v.scopes),
    )("authorizationUrl", "tokenUrl", "refreshUrl", "scopes")

    CodecJson(
      a =>
        Json.obj(
          "openapi" -> jString(a.openapi),
          "info" -> Json.obj("title" -> jString(a.info.title), "version" -> jString(a.info.version)),
        ),
      { c =>
        val info = c.downField("info")
        for {
          openapi <- c.downField("openapi").as[String]
          title <- info.downField("title").as[String]
          version <- info.downField("version").as[String]
        } yield OpenApi(openapi = openapi, info = Info(title = title, version = version))
      },
    )
  }

  private def toSchema(c: HCursor): DecodeResult[Schema] =
    c.fields.map(_.toSet).map { keys =>
      for {
        `type` <- field[String](c, keys, "type")
        title <- field[String](c, keys, "title")
        description <- field[String](c, keys, "description")
        properties <- Option.when(keys.contains(propertiesField)) {
          c.downField(propertiesField).hcursor.map { jsonObject =>
            val objectFields = jsonObject.fields.getOrElse(Seq())
            objectFields.foldLeft(DecodeResult.ok(Map[String, Schema]())) { case (result, key) =>
              result.flatMap { schemas =>
                jsonObject.downField(key).hcursor.map { jsonValue =>
                  toSchema(jsonValue).map(schema => schemas + (key -> schema))
                }.getOrElse(DecodeResult.ok(schemas))
              }
            }.map(Some.apply)
          }.getOrElse(DecodeResult.ok(None))
        }.getOrElse(DecodeResult.ok(None))
        required <- field[List[String]](c, keys, "required")
        default <- field[String](c, keys, "default")
        allOf <- Option.when(keys.contains(allOfField)) {
          c.downField(allOfField).hcursor.map { jsonArray =>
            val arrayIndices = jsonArray.fields.getOrElse(Seq()).indices
            arrayIndices.foldLeft(DecodeResult.ok(List[Schema]())) { case (result, index) =>
              result.flatMap { schemas =>
                jsonArray.downN(index).hcursor.map(jsonValue => toSchema(jsonValue).map(schemas :+ _))
                  .getOrElse(DecodeResult(Right(schemas)))
              }
            }.map(Some.apply)
          }.getOrElse(DecodeResult.ok(None))
        }.getOrElse(DecodeResult.ok(None))
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
    }.getOrElse(DecodeResult(Left("Not a JSON object", c.history)))

  private def fromSchema(schema: Schema): Json = {
    val fields = Seq(
      schema.`type`.map(v => "type" -> jString(v)),
      schema.title.map(v => "title" -> jString(v)),
      schema.description.map(v => "description" -> jString(v)),
      schema.properties.map(v => "properties" -> jObjectFields(v.view.mapValues(fromSchema).toSeq*)),
      schema.required.map(v => "required" -> jArray(v.map(jString))),
      schema.default.map(v => "default" -> jString(v)),
      schema.allOf.map(v => "allOf" -> jArray(v.map(fromSchema))),
      schema.$ref.map(v => "$ref" -> jString(v)),
    ).flatten
    Json.obj(fields*)
  }

  private def field[T](c: HCursor, keys: Set[String], name: String)(implicit
    decoder: DecodeJson[Option[T]]
  ): DecodeResult[Option[T]] =
    if (keys.contains(name)) { c.downField(name).as[Option[T]] }
    else { DecodeResult(Right(None)) }
}
