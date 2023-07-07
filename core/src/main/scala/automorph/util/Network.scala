package automorph.util

/** Network address utilities. */
private[automorph] object Network {

  /**
   * Normalize network address.
   *
   * @param forwardedFor
   *   X-Forwarded-For HTTP header
   * @param address
   *   network address
   * @return
   *   normalized address
   */
  def address(forwardedFor: Option[String], address: String): String =
    forwardedFor.flatMap(_.split(",", 2).headOption).getOrElse {
      val lastPart = address.split("/", 2).last.replaceAll("/", "")
      lastPart.split(":").init.mkString(":")
    }
}
