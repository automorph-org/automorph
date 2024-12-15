package automorph.log

import org.slf4j
import org.slf4j.{LoggerFactory, MDC}
import scala.annotation.nowarn

/**
 * Scala Logging compatible structured logger implicit SLF4J Mapped Diagnostic Context.
 *
 * Can be used as a drop-in replacement for Logger class in Scala Logging.
 *
 * @see
 *   [[https://github.com/lightbend/scala-logging Scala Logging documentation]]
 * @see
 *   [[http://logback.qos.ch/manual/mdc.html MDC concept description]]
 * @param underlying
 *   underlying [[https://www.javadoc.io/doc/org.slf4j/slf4j-api/1.7.30/org/slf4j/Logger.html SLF4J logger]]
 */
@SerialVersionUID(782158461L)
final private[automorph] case class Logger private (private val underlying: slf4j.Logger) {

  private type Not[T] = T => Nothing
  private type Or[T, U] = Not[Not[T] & Not[U]]
  private type Is[T] = Not[Not[T]]
  private type ProductOrIterable = Or[Iterable[(String, Any)], Product]

  def error(message: => String): Unit =
    underlying.error(message)

  def error(message: => String, cause: => Throwable): Unit =
    underlying.error(message, cause)

  def error(message: => String, properties: (String, Any)*): Unit =
    error(message, properties)

  def error[T](message: => String, properties: => T)(implicit evidence: Is[T] <:< ProductOrIterable): Unit =
    log(message, properties, underlying.isErrorEnabled, message => underlying.error(message))

  def error(message: => String, cause: => Throwable, properties: (String, Any)*): Unit =
    error(message, cause, properties)

  def error[T](message: => String, cause: => Throwable, properties: => T)(implicit
    evidence: Is[T] <:< ProductOrIterable
  ): Unit =
    log(message, cause, properties, underlying.isErrorEnabled, (message, cause) => underlying.error(message, cause))

  def isErrorEnabled: Boolean =
    underlying.isErrorEnabled

  def warn(message: => String): Unit =
    underlying.warn(message)

  def warn(message: => String, cause: => Throwable): Unit =
    underlying.warn(message, cause)

  def warn(message: => String, properties: (String, Any)*): Unit =
    warn(message, properties)

  def warn[T](message: => String, properties: => T)(implicit evidence: Is[T] <:< ProductOrIterable): Unit =
    log(message, properties, underlying.isWarnEnabled, message => underlying.warn(message))

  def warn(message: => String, cause: => Throwable, properties: (String, Any)*): Unit =
    warn(message, cause, properties)

  def warn[T](message: => String, cause: => Throwable, properties: => T)(implicit
    evidence: Is[T] <:< ProductOrIterable
  ): Unit =
    log(message, cause, properties, underlying.isWarnEnabled, (message, cause) => underlying.warn(message, cause))

  def isWarnEnabled: Boolean =
    underlying.isWarnEnabled

  def info(message: => String): Unit =
    underlying.info(message)

  def info(message: => String, cause: => Throwable): Unit =
    underlying.info(message, cause)

  def info(message: => String, properties: (String, Any)*): Unit =
    info(message, properties)

  def info[T](message: => String, properties: => T)(implicit evidence: Is[T] <:< ProductOrIterable): Unit =
    log(message, properties, underlying.isInfoEnabled, message => underlying.info(message))

  def info(message: => String, cause: => Throwable, properties: (String, Any)*): Unit =
    info(message, cause, properties)

  def info[T](message: => String, cause: => Throwable, properties: => T)(implicit
    evidence: Is[T] <:< ProductOrIterable
  ): Unit =
    log(message, cause, properties, underlying.isInfoEnabled, (message, cause) => underlying.info(message, cause))

  def isInfoEnabled: Boolean =
    underlying.isInfoEnabled

  def debug(message: => String): Unit =
    underlying.debug(message)

  def debug(message: => String, cause: => Throwable): Unit =
    underlying.debug(message, cause)

  def debug(message: => String, properties: (String, Any)*): Unit =
    debug(message, properties)

  def debug[T](message: => String, properties: => T)(implicit evidence: Is[T] <:< ProductOrIterable): Unit =
    log(message, properties, underlying.isDebugEnabled, message => underlying.debug(message))

  def debug(message: => String, cause: => Throwable, properties: (String, Any)*): Unit =
    debug(message, cause, properties)

  def debug[T](message: => String, cause: => Throwable, properties: => T)(implicit
    evidence: Is[T] <:< ProductOrIterable
  ): Unit =
    log(message, cause, properties, underlying.isDebugEnabled, (message, cause) => underlying.debug(message, cause))

  def isDebugEnabled: Boolean =
    underlying.isDebugEnabled

  def trace(message: => String): Unit =
    underlying.trace(message)

  def trace(message: => String, cause: => Throwable): Unit =
    underlying.trace(message, cause)

  def trace(message: => String, properties: (String, Any)*): Unit =
    trace(message, properties)

  def trace[T](message: => String, properties: => T)(implicit evidence: Is[T] <:< ProductOrIterable): Unit =
    log(message, properties, underlying.isTraceEnabled, message => underlying.trace(message))

  def trace(message: => String, cause: => Throwable, properties: (String, Any)*): Unit =
    trace(message, cause, properties)

  def trace[T](message: => String, cause: => Throwable, properties: => T)(implicit
    evidence: Is[T] <:< ProductOrIterable
  ): Unit =
    log(message, cause, properties, underlying.isTraceEnabled, (message, cause) => underlying.trace(message, cause))

  def isTraceEnabled: Boolean =
    underlying.isTraceEnabled

  @nowarn("msg=unused implicit")
  private def log[T](message: => String, properties: => T, enabled: Boolean, logMessage: String => Unit)(implicit
    evidence: Is[T] <:< ProductOrIterable
  ): Unit =
    if (enabled) {
      val iterableProperties = unpackProperties(properties)
      addDiagnosticContext(iterableProperties)
      logMessage(message)
      removeDiagnosticContext(iterableProperties)
    }

  @nowarn("msg=unused implicit")
  private def log[T](
    message: => String,
    cause: => Throwable,
    properties: => T,
    enabled: Boolean,
    logMessage: (String, Throwable) => Unit,
  )(implicit evidence: Is[T] <:< ProductOrIterable): Unit =
    if (enabled) {
      val iterableProperties = unpackProperties(properties)
      addDiagnosticContext(iterableProperties)
      logMessage(message, cause)
      removeDiagnosticContext(iterableProperties)
    }

  private def unpackProperties[T](properties: => T): Iterable[(String, Any)] =
    properties match {
      case product if product.isInstanceOf[Product] => productProperties(product.asInstanceOf[Product])
      case iterable if iterable.isInstanceOf[Iterable[?]] => iterable.asInstanceOf[Iterable[(String, Any)]]
      case _ => Iterable()
    }

  private def productProperties(product: Product): Map[String, Any] =
    product.productElementNames.map(_.capitalize).zip(product.productIterator).toMap

  private def addDiagnosticContext(properties: Iterable[(String, Any)]): Unit =
    properties.foreach { case (key, value) => MDC.put(key, codec(value)) }

  private def codec(value: Any): String =
    value.toString

  private def removeDiagnosticContext(properties: Iterable[(String, Any)]): Unit =
    properties.foreach { case (key, _) => MDC.remove(key) }
}

private[automorph] object Logger {

  /**
   * Create a logger using the underlying `org.slf4j.Logger`.
   *
   * @param underlying
   *   underlying [[https://www.javadoc.io/doc/org.slf4j/slf4j-api/1.7.30/org/slf4j/Logger.html SLF4J logger]]
   * @return
   *   logger
   */
  def apply(underlying: slf4j.Logger): Logger =
    new Logger(underlying)

  /**
   * Create a logger with the specified name.
   *
   * @param name
   *   logger name
   * @return
   *   logger
   */
  def apply(name: String): Logger =
    new Logger(LoggerFactory.getLogger(name))
}
