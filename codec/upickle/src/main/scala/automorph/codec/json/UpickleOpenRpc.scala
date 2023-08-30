package automorph.codec.json

import automorph.schema.OpenRpc
import automorph.schema.openrpc.*
import ujson.{Arr, Obj, Str, Value}
import upickle.core.Abort

/** OpenRPC schema support for uPickle message codec plugin using JSON format. */
private[automorph] object UpickleOpenRpc {

  def readWriter[Config <: UpickleJsonConfig](config: Config): config.ReadWriter[OpenRpc] = {
    import config.*

    implicit val schemaRw: config.ReadWriter[Schema] = readwriter[Value].bimap[Schema](fromSchema, toSchema)
    implicit val contactRw: config.ReadWriter[Contact] = config.macroRW
    implicit val contentDescriptorRw: config.ReadWriter[ContentDescriptor] = config.macroRW
    implicit val externalDocumentationRw: config.ReadWriter[ExternalDocumentation] = config.macroRW
    implicit val errorRw: config.ReadWriter[Error] = config.macroRW
    implicit val exampleRw: config.ReadWriter[Example] = config.macroRW
    implicit val licenseRw: config.ReadWriter[License] = config.macroRW
    implicit val serverVariableRw: config.ReadWriter[ServerVariable] = config.macroRW
    implicit val examplePairingRw: config.ReadWriter[ExamplePairing] = config.macroRW
    implicit val infoRw: config.ReadWriter[Info] = config.macroRW
    implicit val serverRw: config.ReadWriter[Server] = config.macroRW
    implicit val tagRw: config.ReadWriter[Tag] = config.macroRW
    implicit val linkRw: config.ReadWriter[Link] = config.macroRW
    implicit val componentsRw: config.ReadWriter[Components] = config.macroRW
    implicit val methodRw: config.ReadWriter[Method] = config.macroRW

    config.macroRW[OpenRpc]
  }

  private def fromSchema(schema: Schema): Value =
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

  private def toSchema(node: Value): Schema =
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
      case _ => throw Abort("Invalid OpenRPC object")
    }
}
