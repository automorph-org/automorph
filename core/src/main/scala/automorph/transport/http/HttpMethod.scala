package automorph.transport.http

/** HTTP request method. */
sealed abstract class HttpMethod {

  /** HTTP method name. */
  def name: String =
    toString.toUpperCase
}

object HttpMethod {

  /** HTTP method values. */
  lazy val values: Seq[HttpMethod] = Seq(Get, Head, Post, Put, Delete, Connect, Options, Trace, Patch)

  /**
   * Create HTTP method with specified name.
   *
   * @param name
   *   HTTP method name
   * @return
   *   HTTP method
   */
  def valueOf(name: String): HttpMethod =
    values.find(_.name == name.toUpperCase).getOrElse {
      throw new IllegalArgumentException(s"Invalid HTTP method: $name")
    }

  case object Get extends HttpMethod
  case object Head extends HttpMethod
  case object Post extends HttpMethod
  case object Put extends HttpMethod
  case object Delete extends HttpMethod
  case object Connect extends HttpMethod
  case object Options extends HttpMethod
  case object Trace extends HttpMethod
  case object Patch extends HttpMethod
}
