package automorph.system

import automorph.spi.EffectSystem
import automorph.spi.EffectSystem.Completable
import scalaz.effect.IO
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext, Future, Promise}

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

  override def fold[T, R](effect: => IO[T])(failure: Throwable => R, success: T => R): IO[R] =
    effect.catchLeft.map(_.toEither match {
      case Left(error) => failure(error)
      case Right(result) => success(result)
    })

  override def flatFold[T, R](effect: => IO[T])(failure: Throwable => IO[R], success: T => IO[R]): IO[R] =
    effect.catchLeft.flatMap(_.toEither match {
      case Left(error) => failure(error)
      case Right(result) => success(result)
    })

  override def map[T, R](effect: IO[T])(function: T => R): IO[R] =
    effect.map(function)

  override def flatMap[T, R](effect: IO[T])(function: T => IO[R]): IO[R] =
    effect.flatMap(function)

  override def runAsync[T](effect: IO[T]): Unit = {
    Future(effect)
    ()
  }

  override def completable[T]: IO[EffectSystem.Completable[IO, T]] =
    IO(CompletableFuture())

  sealed private case class CompletableFuture[T]() extends Completable[IO, T]() {
    private val promise: Promise[T] = Promise()

    override def effect: IO[T] =
      IO(Await.result(promise.future, Duration.Inf))

    override def succeed(value: T): IO[Unit] =
      IO(promise.success(value))

    override def fail(exception: Throwable): IO[Unit] =
      IO(promise.failure(exception))
  }
}

object ScalazEffectSystem {

  /** Effect type. */
  type Effect[T] = IO[T]
}
