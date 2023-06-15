package automorph.codec.json

import automorph.schema.OpenRpc
import automorph.schema.openrpc.*
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.{ACursor, Decoder, DecodingFailure, Encoder, HCursor, Json}

/** JSON-RPC protocol support for Circe message codec plugin using JSON format. */
private[automorph] case object CirceOpenRpc {

  def openRpcEncoder: Encoder[OpenRpc] = {
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
    implicit val contactEncoder: Encoder[Contact] = deriveEncoder[Contact]
    implicit val contentDescriptorEncoder: Encoder[ContentDescriptor] = deriveEncoder[ContentDescriptor]
    implicit val externalDocumentationEncoder: Encoder[ExternalDocumentation] = deriveEncoder[ExternalDocumentation]
    implicit val errorEncoder: Encoder[Error] = deriveEncoder[Error]
    implicit val exampleEncoder: Encoder[Example] = deriveEncoder[Example]
    implicit val licenseEncoder: Encoder[License] = deriveEncoder[License]
    implicit val serverVariableEncoder: Encoder[ServerVariable] = deriveEncoder[ServerVariable]
    implicit val examplePairingEncoder: Encoder[ExamplePairing] = deriveEncoder[ExamplePairing]
    implicit val infoEncoder: Encoder[Info] = deriveEncoder[Info]
    implicit val serverEncoder: Encoder[Server] = deriveEncoder[Server]
    implicit val tagEncoder: Encoder[Tag] = deriveEncoder[Tag]
    implicit val linkEncoder: Encoder[Link] = deriveEncoder[Link]
    implicit val componentsEncoder: Encoder[Components] = deriveEncoder[Components]
    implicit val methodEncoder: Encoder[Method] = deriveEncoder[Method]

    deriveEncoder[OpenRpc]
  }

  def openRpcDecoder: Decoder[OpenRpc] = {
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
    implicit val contactDecoder: Decoder[Contact] = deriveDecoder[Contact]
    implicit val contentDescriptorDecoder: Decoder[ContentDescriptor] = deriveDecoder[ContentDescriptor]
    implicit val externalDocumentationDecoder: Decoder[ExternalDocumentation] = deriveDecoder[ExternalDocumentation]
    implicit val errorDecoder: Decoder[Error] = deriveDecoder[Error]
    implicit val exampleDecoder: Decoder[Example] = deriveDecoder[Example]
    implicit val licenseDecoder: Decoder[License] = deriveDecoder[License]
    implicit val serverVariableDecoder: Decoder[ServerVariable] = deriveDecoder[ServerVariable]
    implicit val examplePairingDecoder: Decoder[ExamplePairing] = deriveDecoder[ExamplePairing]
    implicit val infoDecoder: Decoder[Info] = deriveDecoder[Info]
    implicit val serverDecoder: Decoder[Server] = deriveDecoder[Server]
    implicit val tagDecoder: Decoder[Tag] = deriveDecoder[Tag]
    implicit val linkDecoder: Decoder[Link] = deriveDecoder[Link]
    implicit val componentsDecoder: Decoder[Components] = deriveDecoder[Components]
    implicit val methodDecoder: Decoder[Method] = deriveDecoder[Method]

    deriveDecoder[OpenRpc]
  }
}
