package automorph.codec.messagepack

import automorph.schema.OpenRpc
import automorph.schema.openrpc.*
import scala.annotation.nowarn
import upack.{Arr, Msg, Obj, Str}
import upickle.core.{Abort, LinkedHashMap}

/** JSON-RPC protocol support for Circe message codec plugin using JSON format. */
private[automorph] case object UpickleOpenRpc {

  @nowarn("msg=used")
  def readWriter[Custom <: UpickleMessagePackCustom](custom: Custom): custom.ReadWriter[OpenRpc] = {
    import custom.*

    implicit val schemaRw: custom.ReadWriter[Schema] = readwriter[Msg].bimap[Schema](fromSchema, toSchema)
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

  private def fromSchema(schema: Schema): Msg =
    Obj(LinkedHashMap[Msg, Msg](
      Seq(
        schema.`type`.map(Str("type") -> Str(_)),
        schema.title.map(Str("title") -> Str(_)),
        schema.description.map(Str("description") -> Str(_)),
        schema.properties.map(v =>
          Str("properties") -> Obj(LinkedHashMap[Msg, Msg](v.map { case (key, value) =>
            Str(key) -> fromSchema(value)
          }))
        ),
        schema.required.map(v => Str("required") -> Arr(v.map(Str.apply)*)),
        schema.default.map(Str("default") -> Str(_)),
        schema.allOf.map(v => Str("allOf") -> Arr(v.map(fromSchema)*)),
        schema.$ref.map(Str("$ref") -> Str(_)),
      ).flatten
    ))

  private def toSchema(node: Msg): Schema =
    node match {
      case Obj(fields) => Schema(
          `type` = fields.get(Str("type")).map(_.str),
          title = fields.get(Str("title")).map(_.str),
          description = fields.get(Str("description")).map(_.str),
          properties = fields.get(Str("properties")).map(_.obj.map { case (key, value) => key.str -> toSchema(value) }
            .toMap),
          required = fields.get(Str("required")).map(_.arr.map(_.str).toList),
          default = fields.get(Str("default")).map(_.str),
          allOf = fields.get(Str("allOf")).map(_.arr.map(toSchema).toList),
          $ref = fields.get(Str("$ref")).map(_.str),
        )
      case _ => throw Abort(s"Invalid OpenRPC object")
    }
}
