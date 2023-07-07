package automorph

import automorph.RpcFunction.Parameter

/**
 * Remote function descriptor.
 *
 * @param name
 *   name
 * @param resultType
 *   result type
 * @param parameters
 *   parameters
 * @param documentation
 *   documentation (Scaladoc)
 */
final case class RpcFunction(
  name: String,
  parameters: Seq[Parameter],
  resultType: String,
  documentation: Option[String],
) {

  override def toString: String =
    s"$name(${parameters.mkString(", ")}): $resultType"
}

object RpcFunction {

  /**
   * Function parameter descriptor.
   *
   * @param name
   *   name
   * @param `type`
   *   type
   */
  final case class Parameter(name: String, `type`: String) {
    override def toString =
      s"$name: ${`type`}"
  }
}
