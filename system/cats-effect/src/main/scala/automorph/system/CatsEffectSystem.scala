package automorph.system

import automorph.spi.EffectSystem
import automorph.spi.EffectSystem.Completable
import cats.effect.IO
import cats.effect.std.Queue
import cats.effect.unsafe.IORuntime
import scala.concurrent.duration.FiniteDuration

/**
 * Cats Effect effect system plugin.
 *
 * @see
 *   [[https://typelevel.org/cats-effect/ Library documentation]]
 * @see
 *   [[https://typelevel.org/cats-effect/api/3.x/cats/effect/IO.html Effect type]]
 * @constructor
 *   Creates a Cats Effect effect system plugin using `IO` as an effect type.
 * @param runtime
 *   runtime system
 */
final case class CatsEffectSystem()(implicit val runtime: IORuntime) extends EffectSystem[IO] {

  override def evaluate[T](value: => T): IO[T] =
    IO(value)

  override def successful[T](value: T): IO[T] =
    IO.pure(value)

  override def failed[T](exception: Throwable): IO[T] =
    IO.raiseError(exception)

  override def either[T](effect: => IO[T]): IO[Either[Throwable, T]] =
    effect.attempt

  override def fold[T, R](effect: => IO[T])(failure: Throwable => R, success: T => R): IO[R] =
    effect.attempt.map {
      case Left(error) => failure(error)
      case Right(result) => success(result)
    }

  override def flatFold[T, R](effect: => IO[T])(failure: Throwable => IO[R], success: T => IO[R]): IO[R] =
    effect.attempt.flatMap {
      case Left(error) => failure(error)
      case Right(result) => success(result)
    }

  override def map[T, R](effect: IO[T])(function: T => R): IO[R] =
    effect.map(function)

  override def flatMap[T, R](effect: IO[T])(function: T => IO[R]): IO[R] =
    effect.flatMap(function)

  override def sleep(duration: FiniteDuration): IO[Unit] =
    IO.sleep(duration)

  override def runAsync[T](effect: => IO[T]): Unit =
    effect.unsafeRunAndForget()

  override def completable[T]: IO[Completable[IO, T]] =
    map(Queue.dropping[IO, Either[Throwable, T]](1))(CompletableIO.apply)

  sealed private case class CompletableIO[T](private val queue: Queue[IO, Either[Throwable, T]])
    extends Completable[IO, T]() {

    override def effect: IO[T] =
      queue.take.flatMap {
        case Right(value) => successful(value)
        case Left(exception) => failed(exception)
      }

    override def succeed(value: T): IO[Unit] =
      flatMap(queue.tryOffer(Right(value)))(complete)

    override def fail(exception: Throwable): IO[Unit] =
      flatMap(queue.tryOffer(Left(exception)))(complete)

    private def complete(success: Boolean): IO[Unit] =
      Option.when(success)(successful {}).getOrElse {
        failed(new IllegalStateException("Completable effect already resolved"))
      }
  }
}

object CatsEffectSystem {

  /** Effect type. */
  type Effect[T] = IO[T]
}
