package automorph.system

import automorph.spi.AsyncEffectSystem
import automorph.spi.AsyncEffectSystem.Completable
import zio.{Queue, RIO, Runtime, Trace, Unsafe, ZIO}

/**
 * ZIO effect system plugin using `RIO` as an effect type.
 *
 * @see
 *   [[https://zio.dev Library documentation]]
 * @see
 *   [[https://javadoc.io/doc/dev.zio/zio_3/latest/zio.html#RIO-0 Effect type]]
 * @constructor
 *   Creates a ZIO effect system plugin using `RIO` as an effect type.
 * @param runtime
 *   runtime system
 * @tparam Environment
 *   ZIO environment type
 */
final case class ZioSystem[Environment]()(implicit val runtime: Runtime[Environment])
  extends AsyncEffectSystem[({ type Effect[A] = RIO[Environment, A] })#Effect] {

  override def evaluate[T](value: => T): RIO[Environment, T] =
    ZIO.attempt(value)

  override def successful[T](value: T): RIO[Environment, T] =
    ZIO.succeed(value)

  override def failed[T](exception: Throwable): RIO[Environment, T] =
    ZIO.fail(exception)

  override def either[T](effect: => RIO[Environment, T]): RIO[Environment, Either[Throwable, T]] =
    effect.either

  override def flatMap[T, R](effect: RIO[Environment, T])(function: T => RIO[Environment, R]): RIO[Environment, R] =
    effect.flatMap(function)

  override def runAsync[T](effect: RIO[Environment, T]): Unit = {
    implicit val trace: Trace = Trace.empty
    Unsafe.unsafe { implicit unsafe =>
      runtime.unsafe.fork(effect)
      ()
    }
  }

  override def completable[T]: RIO[Environment, Completable[({ type Effect[A] = RIO[Environment, A] })#Effect, T]] =
    map(Queue.dropping[Either[Throwable, T]](1))(CompletableRIO.apply)

  private sealed case class CompletableRIO[T](private val queue: Queue[Either[Throwable, T]])
    extends Completable[({ type Effect[A] = RIO[Environment, A] })#Effect, T]() {

    override def effect: RIO[Environment, T] =
      queue.take.flatMap {
        case Right(value) => successful(value)
        case Left(exception) => failed(exception)
      }

    override def succeed(value: T): RIO[Environment, Unit] =
      map(queue.offer(Right(value)))(_ => ())

    override def fail(exception: Throwable): RIO[Environment, Unit] =
      map(queue.offer(Left(exception)))(_ => ())
  }
}

case object ZioSystem {

  /**
   * ZIO effect type with specified environment.
   *
   * @tparam T
   *   effectful value type
   * @tparam Environment
   *   effectful ZIO environment type
   */
  type Effect[T, Environment] = RIO[Environment, T]

  /**
   * Creates a ZIO effect system plugin with default environment using `RIO` as an effect type.
   *
   * @see
   *   [[https://zio.dev Library documentation]]
   * @see
   *   [[https://javadoc.io/doc/dev.zio/zio_3/latest/zio.html#RIO-0 Effect type]]
   * @return
   *   ZIO effect system plugin
   */
  def default: ZioSystem[Any] =
    ZioSystem[Any]()(defaultRuntime)

  /** Default ZIO runtime environment. */
  def defaultRuntime: Runtime[Any] =
    Runtime.default
}
