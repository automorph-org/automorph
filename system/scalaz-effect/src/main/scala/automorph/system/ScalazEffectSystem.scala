package automorph.system

import automorph.spi.EffectSystem
import scala.concurrent.{ExecutionContext, Future}
import scalaz.effect.IO

/**
 * Scalaz effect system plugin using `IO` as an effect type.
 *
 * @see
 *   [[https://github.com/scalaz Library documentation]]
 * @see
 *   [[https://www.javadoc.io/doc/org.scalaz/scalaz_2.13/latest/scalaz/effect/IO.html Effect type]]
 * @constructor
 *   Creates a Scalaz effect system plugin using `IO` as an effect type.
 * @param executionContext
 *   execution context
 */
final case class ScalazEffectSystem()(implicit val executionContext: ExecutionContext) extends EffectSystem[IO] {

  override def evaluate[T](value: => T): IO[T] =
    IO(value)

  override def successful[T](value: T): IO[T] =
    IO(value)

  override def failed[T](exception: Throwable): IO[T] =
    IO.throwIO(exception)

  override def either[T](effect: => IO[T]): IO[Either[Throwable, T]] =
    effect.catchLeft.map(_.toEither)

  override def flatMap[T, R](effect: IO[T])(function: T => IO[R]): IO[R] =
    effect.flatMap(function)

  override def runAsync[T](effect: IO[T]): Unit = {
    Future(effect)
    ()
  }
}

object ScalazEffectSystem {

  /**
   * Effect type.
   *
   * @tparam T
   *   value type
   */
  type Effect[T] = IO[T]
}
