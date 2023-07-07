package automorph.codec.json

import automorph.schema.OpenRpc
import automorph.schema.openrpc.*
import ujson.{Arr, Obj, Str, Value}
import upickle.core.Abort

/** JSON-RPC protocol support for Circe message codec plugin using JSON format. */
private[automorph] object UpickleOpenRpc {

  def readWriter[Custom <: UpickleJsonCustom](custom: Custom): custom.ReadWriter[OpenRpc] = {
    import custom.*

    implicit val schemaRw: custom.ReadWriter[Schema] = readwriter[Value].bimap[Schema](fromSchema, toSchema)
    implicit val contactRw: custom.ReadWriter[Contact] = custom.macroRW
    implicit val contentDescriptorRw: custom.ReadWriter[ContentDescriptor] = custom.macroRW
    implicit val externalDocumentationRw: custom.ReadWriter[ExternalDocumentation] = custom.macroRW
    implicit val errorRw: custom.ReadWriter[Error] = custom.macroRW
    implicit val exampleRw: custom.ReadWriter[Example] = custom.macroRW
    implicit val licenseRw: custom.ReadWriter[License] = custom.macroRW
    implicit val serverVariableRw: custom.ReadWriter[ServerVariable] = custom.macroRW
    implicit val examplePairingRw: custom.ReadWriter[ExamplePairing] = custom.macroRW
    implicit val infoRw: custom.ReadWriter[Info] = custom.macroRW
    implicit val serverRw: custom.ReadWriter[Server] = custom.macroRW
    implicit val tagRw: custom.ReadWriter[Tag] = custom.macroRW
    implicit val linkRw: custom.ReadWriter[Link] = custom.macroRW
    implicit val componentsRw: custom.ReadWriter[Components] = custom.macroRW
    implicit val methodRw: custom.ReadWriter[Method] = custom.macroRW

    custom.macroRW[OpenRpc]
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
      case _ => throw Abort(s"Invalid OpenRPC object")
    }
}
