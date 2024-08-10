package automorph.log

/**
 * Scala Logging compatible structured logger implicit SLF4J Mapped Diagnostic Context.
 *
 * Can be used as a drop-in replacement for Logger class in Scala Logging.
 *
 * @see
 *   [[https://github.com/lightbend/scala-logging Scala Logging documentation]]
 * @see
 *   [[http://logback.qos.ch/manual/mdc.html MDC concept description]]
 * @constructor
 *   Creates a logger using the underlying `org.slf4j.Logger`.
 * @param underlying
 *   underlying [[https://www.javadoc.io/doc/org.slf4j/slf4j-api/1.7.30/org/slf4j/Logger.html SLF4J logger]]
 */
final private[automorph] case class Logger(name: String) {

  type Not[T] = T => Nothing
  type Or[T, U] = Not[Not[T] & Not[U]]

  def error(message: => String): Unit =
    println(message)
//    underlying.error(message)

  def error(message: => String, cause: => Throwable): Unit =
    println(message + ": " + cause)
//    underlying.error(message, cause)

  def error(message: => String, properties: (String, Any)*): Unit =
    error(message, properties)
//    error(message, properties)

  def error[T](message: => String, properties: => T)(implicit
    evidence: Not[Not[T]] <:< Or[Iterable[(String, Any)], Product]
  ): Unit =
    log(message, properties, /*underlying.*/isErrorEnabled, message => /*underlying.*/error(message))

  def error(message: => String, cause: => Throwable, properties: (String, Any)*): Unit =
    error(message, cause, properties)

  def error[T](message: => String, cause: => Throwable, properties: => T)(implicit
    evidence: Not[Not[T]] <:< Or[Iterable[(String, Any)], Product]
  ): Unit =
    log(message, cause, properties, /*underlying.*/isErrorEnabled, (message, cause) => /*underlying.*/error(message, cause))

  def isErrorEnabled: Boolean =
    true
//    underlying.isErrorEnabled

  def warn(message: => String): Unit =
    println(message)
//    underlying.warn(message)

  def warn(message: => String, cause: => Throwable): Unit =
    println(message + ": " + cause)
//    underlying.warn(message, cause)

  def warn(message: => String, properties: (String, Any)*): Unit =
    warn(message, properties)

  def warn[T](message: => String, properties: => T)(implicit
    evidence: Not[Not[T]] <:< Or[Iterable[(String, Any)], Product]
  ): Unit =
    log(message, properties, /*underlying.*/isWarnEnabled, message => /*underlying.*/warn(message))

  def warn(message: => String, cause: => Throwable, properties: (String, Any)*): Unit =
    warn(message, cause, properties)

  def warn[T](message: => String, cause: => Throwable, properties: => T)(implicit
    evidence: Not[Not[T]] <:< Or[Iterable[(String, Any)], Product]
  ): Unit =
    log(message, cause, properties, /*underlying.*/isWarnEnabled, (message, cause) => /*underlying.*/warn(message, cause))

  def isWarnEnabled: Boolean =
    true
//    underlying.isWarnEnabled

  def info(message: => String): Unit =
    println(message)
//    underlying.info(message)

  def info(message: => String, cause: => Throwable): Unit =
    println(message + ": " + cause)
//    underlying.info(message, cause)

  def info(message: => String, properties: (String, Any)*): Unit =
    info(message, properties)

  def info[T](message: => String, properties: => T)(implicit
    evidence: Not[Not[T]] <:< Or[Iterable[(String, Any)], Product]
  ): Unit =
    log(message, properties, /*underlying.*/isInfoEnabled, message => /*underlying.*/info(message))

  def info(message: => String, cause: => Throwable, properties: (String, Any)*): Unit =
    info(message, cause, properties)

  def info[T](message: => String, cause: => Throwable, properties: => T)(implicit
    evidence: Not[Not[T]] <:< Or[Iterable[(String, Any)], Product]
  ): Unit =
    log(message, cause, properties, /*underlying.*/isInfoEnabled, (message, cause) => /*underlying.*/info(message, cause))

  def isInfoEnabled: Boolean =
    true
//    underlying.isInfoEnabled

  def debug(message: => String): Unit =
    println(message)
//    underlying.debug(message)

  def debug(message: => String, cause: => Throwable): Unit =
    println(message + ": " + cause)
//    underlying.debug(message, cause)

  def debug(message: => String, properties: (String, Any)*): Unit =
    debug(message, properties)

  def debug[T](message: => String, properties: => T)(implicit
    evidence: Not[Not[T]] <:< Or[Iterable[(String, Any)], Product]
  ): Unit =
    log(message, properties, /*underlying.*/isDebugEnabled, message => /*underlying.*/debug(message))

  def debug(message: => String, cause: => Throwable, properties: (String, Any)*): Unit =
    debug(message, cause, properties)

  def debug[T](message: => String, cause: => Throwable, properties: => T)(implicit
    evidence: Not[Not[T]] <:< Or[Iterable[(String, Any)], Product]
  ): Unit =
    log(message, cause, properties, /*underlying.*/isDebugEnabled, (message, cause) => /*underlying.*/debug(message, cause))

  def isDebugEnabled: Boolean =
    true
//    underlying.isDebugEnabled

  def trace(message: => String): Unit =
    println(message)
//    underlying.trace(message)

  def trace(message: => String, cause: => Throwable): Unit =
    println(message + ": " + cause)
//    underlying.trace(message, cause)

  def trace(message: => String, properties: (String, Any)*): Unit =
    trace(message, properties)

  def trace[T](message: => String, properties: => T)(implicit
    evidence: Not[Not[T]] <:< Or[Iterable[(String, Any)], Product]
  ): Unit =
    log(message, properties, /*underlying.*/isTraceEnabled, message => /*underlying.*/trace(message))

  def trace(message: => String, cause: => Throwable, properties: (String, Any)*): Unit =
    trace(message, cause, properties)

  def trace[T](message: => String, cause: => Throwable, properties: => T)(implicit
    evidence: Not[Not[T]] <:< Or[Iterable[(String, Any)], Product]
  ): Unit =
    log(message, cause, properties, /*underlying.*/isTraceEnabled, (message, cause) => /*underlying.*/trace(message, cause))

  def isTraceEnabled: Boolean =
    true
//    underlying.isTraceEnabled

  @scala.annotation.nowarn("msg=unused implicit")
  private def log[T](message: => String, properties: => T, enabled: Boolean, logMessage: String => Unit)(implicit
    evidence: Not[Not[T]] <:< Or[Iterable[(String, Any)], Product]
  ): Unit =
    if (enabled) {
      val iterableProperties = unpackProperties(properties)
      addDiagnosticContext(iterableProperties)
      logMessage(message)
      removeDiagnosticContext(iterableProperties)
    }

  @scala.annotation.nowarn("msg=unused implicit")
  private def log[T](
    message: => String,
    cause: => Throwable,
    properties: => T,
    enabled: Boolean,
    logMessage: (String, Throwable) => Unit,
  )(implicit evidence: Not[Not[T]] <:< Or[Iterable[(String, Any)], Product]): Unit =
    if (enabled) {
      val iterableProperties = unpackProperties(properties)
      addDiagnosticContext(iterableProperties)
      logMessage(message, cause)
      removeDiagnosticContext(iterableProperties)
    }

  private def unpackProperties[T](properties: => T): Iterable[(String, Any)] =
    properties match {
      case product: Product => productProperties(product)
      case iterable: Iterable[?] => iterable.asInstanceOf[Iterable[(String, Any)]]
      case _ => Iterable()
    }

  private def productProperties(product: Product): Map[String, Any] =
    product.productElementNames.map(_.capitalize).zip(product.productIterator).toMap

  private def addDiagnosticContext(properties: Iterable[(String, Any)]): Unit = {}
//    properties.foreach { case (key, value) => MDC.put(key, codec(value)) }

//  private def codec(value: Any): String =
//    value.toString

  private def removeDiagnosticContext(properties: Iterable[(String, Any)]): Unit = {}
//    properties.foreach { case (key, _) => MDC.remove(key) }
}

private[automorph] object Logger {

  /**
   * Creates a logger with the specified name.
   *
   * @param name
   *   logger name
   * @return
   *   logger
   */
  def apply(name: String): Logger =
    new Logger(name)
}
