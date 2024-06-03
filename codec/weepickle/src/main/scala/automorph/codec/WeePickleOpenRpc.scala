package automorph.codec

import automorph.schema.{OpenRpc, Schema}
import automorph.schema.openrpc.*
import com.rallyhealth.weejson.v1.{Arr, Obj, Str, Value}
import com.rallyhealth.weepickle.v1.WeePickle.{From, FromTo, FromValue, To, ToValue, macroFromTo}
import com.rallyhealth.weepickle.v1.core.Abort

/** OpenRPC schema support for weePickle message codec plugin. */
private[automorph] object WeePickleOpenRpc {

  def fromTo: FromTo[OpenRpc] = {
    implicit val schemaFrom: From[Schema] = FromValue.comap(fromSchema)
    implicit val schemaTo: To[Schema] = ToValue.map(toSchema)
    implicit val contactFromTo: FromTo[Contact] = macroFromTo
    implicit val contentDescriptorFromTo: FromTo[ContentDescriptor] = macroFromTo
    implicit val externalDocumentationFromTo: FromTo[ExternalDocumentation] = macroFromTo
    implicit val errorFromTo: FromTo[Error] = macroFromTo
    implicit val exampleFromTo: FromTo[Example] = macroFromTo
    implicit val licenseFromTo: FromTo[License] = macroFromTo
    implicit val serverVariableFromTo: FromTo[ServerVariable] = macroFromTo
    implicit val examplePairingFromTo: FromTo[ExamplePairing] = macroFromTo
    implicit val infoFromTo: FromTo[Info] = macroFromTo
    implicit val serverFromTo: FromTo[Server] = macroFromTo
    implicit val tagFromTo: FromTo[Tag] = macroFromTo
    implicit val linkFromTo: FromTo[Link] = macroFromTo
    implicit val componentsFromTo: FromTo[Components] = macroFromTo
    implicit val methodFromTo: FromTo[Method] = macroFromTo
    macroFromTo[OpenRpc]
  }

  private[automorph] def fromSchema(schema: Schema): Value =
    Obj.from(
      Seq(
        schema.`type`.map("type" -> Str(_)),
        schema.title.map("title" -> Str(_)),
        schema.description.map("description" -> Str(_)),
        schema.properties.map(v => "properties" -> Obj.from(v.view.mapValues(fromSchema).toSeq)),
        schema.required.map(v => "required" -> Arr.from(v.map(Str.apply))),
        schema.default.map("default" -> Str(_)),
        schema.allOf.map(v => "allOf" -> Arr.from(v.map(fromSchema))),
        schema.$ref.map("$ref" -> Str(_)),
      ).flatten
    )

  private[automorph] def toSchema(node: Value): Schema =
    node match {
      case Obj(fields) => Schema(
          `type` = fields.get("type").map(_.str),
          title = fields.get("title").map(_.str),
          description = fields.get("description").map(_.str),
          properties = fields.get("properties").map(_.obj.view.mapValues(toSchema).toMap),
          required = fields.get("required").map(_.arr.map(_.str).toList),
          default = fields.get("default").map(_.str),
          allOf = fields.get("allOf").map(_.arr.map(toSchema).toList),
          $ref = fields.get("$ref").map(_.str),
        )
      case _ => throw new Abort("Invalid OpenRPC object")
    }
}
