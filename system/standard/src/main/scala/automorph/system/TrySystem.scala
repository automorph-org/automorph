package automorph.system

import automorph.spi.EffectSystem
import scala.util.{Failure, Success, Try}

/**
 * Synchronous effect system plugin using `Try` as an effect type.
 *
 * @see
 *   [[https://docs.scala-lang.org/scala3/book/fp-functional-error-handling.html Library documentation]]
 * @see
 *   [[https://www.scala-lang.org/files/archive/api/3.x/scala/util/Try.html Effect type]]
 * @constructor
 *   Creates a synchronous effect system plugin using Try as an effect type.
 */
final case class TrySystem() extends EffectSystem[Try] {

  override def evaluate[T](value: => T): Try[T] =
    Try(value)

  override def successful[T](value: T): Try[T] =
    Success(value)

  override def failed[T](exception: Throwable): Try[T] =
    Failure(exception)

  override def either[T](effect: => Try[T]): Try[Either[Throwable, T]] =
    Success(effect.toEither)

  override def flatMap[T, R](effect: Try[T])(function: T => Try[R]): Try[R] =
    effect.flatMap(function)

  override def runAsync[T](effect: Try[T]): Unit =
    ()
}

case object TrySystem {

  /**
   * Effect type.
   *
   * @tparam T
   *   value type
   */
  type Effect[T] = Try[T]
}
