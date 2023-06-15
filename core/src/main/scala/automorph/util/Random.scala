package automorph.util

/** Random value generator. */
private[automorph] case object Random {

  private lazy val random = new scala.util.Random(
    (System.nanoTime()
      ^ (new Object().hashCode().toLong << 32) + new Object().hashCode().toLong)
      ^ Runtime.getRuntime.totalMemory()
  )

  /**
   * Generate random numeric identifier.
   *
   * @return
   *   numeric identifier
   */
  def id: String =
    Math.abs(random.nextInt()).toString
}
