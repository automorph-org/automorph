package automorph.log

/** Compile-time logger used in macros. */
private[automorph] object MacroLogger {

  /** Enable generated code logging environment variable. */
  private val logCodeEnvironment = "LOG_CODE"

  def debug(message: => String): Unit =
    Option(System.getenv(logCodeEnvironment)).foreach(_ => println(message))
}
