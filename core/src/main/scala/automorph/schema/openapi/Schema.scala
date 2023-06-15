package automorph.schema.openapi

import automorph.RpcFunction
import automorph.schema.openapi.Schema.Properties

final case class Schema(
  `type`: Option[String] = None,
  title: Option[String] = None,
  description: Option[String] = None,
  properties: Option[Properties] = None,
  required: Option[List[String]] = None,
  default: Option[String] = None,
  allOf: Option[List[Schema]] = None,
  $ref: Option[String] = None,
)

case object Schema {

  type Properties = Map[String, Schema]

  private val optionTypePrefix = s"${classOf[Option[Unit]].getSimpleName}"

  private[automorph] def requiredParameters(function: RpcFunction): Seq[String] =
    function.parameters.filterNot(_.`type`.startsWith(optionTypePrefix)).map(_.name)

  private[automorph] def parameters(function: RpcFunction): Map[String, Schema] =
    function.parameters.map { parameter =>
      // TODO - convert data type to JSON type
      parameter.name -> Schema(
        Some(parameter.`type`),
        Some(parameter.name),
        scaladocField(function.documentation, s"param ${parameter.name}"),
      )
    }.toMap

  private[automorph] def result(function: RpcFunction): Schema =
    // TODO - convert data type to JSON type
    Schema(Some(function.resultType), Some("result"), scaladocField(function.documentation, "return"))

  private def scaladocField(scaladoc: Option[String], field: String): Option[String] = {
    val fieldPrefix = s"$field "
    scaladoc.flatMap { doc =>
      val description = doc.split('\n').flatMap { line =>
        line.split('@') match {
          case Array(_, tag, rest @ _*) if tag.startsWith(fieldPrefix) =>
            Some((tag.substring(fieldPrefix.length) +: rest).mkString("@").trim)
          case _ => None
        }
      }.toSeq
      Option(description).filter(_.nonEmpty)
    }.map(_.mkString(" "))
  }
}
