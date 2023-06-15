package automorph.system

import automorph.spi.AsyncEffectSystem
import automorph.spi.AsyncEffectSystem.Completable
import cats.effect.IO
import cats.effect.std.Queue
import cats.effect.unsafe.IORuntime

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
final case class CatsEffectSystem()(implicit val runtime: IORuntime) extends AsyncEffectSystem[IO] {

  override def evaluate[T](value: => T): IO[T] =
    IO(value)

  override def successful[T](value: T): IO[T] =
    IO.pure(value)

  override def failed[T](exception: Throwable): IO[T] =
    IO.raiseError(exception)

  override def either[T](effect: => IO[T]): IO[Either[Throwable, T]] =
    effect.attempt

  override def flatMap[T, R](effect: IO[T])(function: T => IO[R]): IO[R] =
    effect.flatMap(function)

  override def runAsync[T](effect: IO[T]): Unit =
    effect.unsafeRunAndForget()

  override def completable[T]: IO[Completable[IO, T]] =
    map(Queue.dropping[IO, Either[Throwable, T]](1))(CompletableIO.apply)

  private sealed case class CompletableIO[T](private val queue: Queue[IO, Either[Throwable, T]])
    extends Completable[IO, T]() {

    override def effect: IO[T] =
      queue.take.flatMap {
        case Right(value) => successful(value)
        case Left(exception) => failed(exception)
      }

    override def succeed(value: T): IO[Unit] =
      flatMap(queue.tryOffer(Right(value))) { success =>
        Option.when(success)(successful {}).getOrElse {
          failed(new IllegalStateException("Completable effect already resolved"))
        }
      }

    override def fail(exception: Throwable): IO[Unit] =
      flatMap(queue.tryOffer(Left(exception))) { success =>
        Option.when(success)(successful {}).getOrElse {
          failed(new IllegalStateException("Completable effect already resolved"))
        }
      }
  }
}

case object CatsEffectSystem {

  /**
   * Effect type.
   *
   * @tparam T
   *   value type
   */
  type Effect[T] = IO[T]
}
