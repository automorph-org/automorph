package automorph.codec.messagepack

import automorph.schema.OpenRpc
import automorph.schema.openrpc.*
import com.rallyhealth.weepack.v1.Msg.{FromMsgValue, ToMsgValue}
import com.rallyhealth.weepack.v1.{Arr, Msg, Obj, Str}
import com.rallyhealth.weepickle.v1.WeePickle.{From, FromTo, To, macroFromTo}
import com.rallyhealth.weepickle.v1.core.Abort
import scala.collection.mutable

/** OpenRPC schema support for weePickle message codec plugin. */
private[automorph] object WeePickleOpenRpc {

  @scala.annotation.nowarn("msg=never used")
  def fromTo: FromTo[OpenRpc] = {
    implicit val schemaFrom: From[Schema] = FromMsgValue.comap(fromSchema)
    implicit val schemaTo: To[Schema] = ToMsgValue.map(toSchema)
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

  private def fromSchema(schema: Schema): Msg =
    Obj(mutable.LinkedHashMap.newBuilder[Msg, Msg].addAll(
      Seq(
        schema.`type`.map(Str("type") -> Str(_)),
        schema.title.map(Str("title") -> Str(_)),
        schema.description.map(Str("description") -> Str(_)),
        schema.properties.map(v =>
          Str("properties") -> Obj(mutable.LinkedHashMap.newBuilder[Msg, Msg].addAll(v.map { case (key, value) =>
            Str(key) -> fromSchema(value)
          }).result())
        ),
        schema.required.map(v => Str("required") -> Arr(v.map(Str.apply)*)),
        schema.default.map(Str("default") -> Str(_)),
        schema.allOf.map(v => Str("allOf") -> Arr(v.map(fromSchema)*)),
        schema.$ref.map(Str("$ref") -> Str(_)),
      ).flatten
    ).result())

  private def toSchema(node: Msg): Schema =
    node match {
      case Obj(fields) => Schema(
          `type` = fields.get(Str("type")).map(_.str),
          title = fields.get(Str("title")).map(_.str),
          description = fields.get(Str("description")).map(_.str),
          properties = fields.get(Str("properties")).map(_.obj.map { case (key, value) =>
            key.str -> toSchema(value)
          }.toMap),
          required = fields.get(Str("required")).map(_.arr.map(_.str).toList),
          default = fields.get(Str("default")).map(_.str),
          allOf = fields.get(Str("allOf")).map(_.arr.map(toSchema).toList),
          $ref = fields.get(Str("$ref")).map(_.str),
        )
      case _ => throw new Abort("Invalid OpenRPC object")
    }
}
