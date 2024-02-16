package automorph.system

import automorph.spi.AsyncEffectSystem
import automorph.spi.AsyncEffectSystem.Completable
import zio.{Queue, Runtime, Trace, Unsafe, ZIO}

/**
 * ZIO effect system plugin using `RIO` as an effect type.
 *
 * @see
 *   [[https://zio.dev Library documentation]]
 * @see
 *   [[https://javadoc.io/doc/dev.zio/zio_3/latest/zio.html Effect type]]
 * @constructor
 *   Creates a ZIO effect system plugin using `RIO` as an effect type.
 * @param mapException
 *   maps an exception to a corresponding ZIO error
 * @param mapError
 *   maps a ZIO error to a corresponding exception
 * @param runtime
 *   ZIO runtime
 * @tparam Environment
 *   ZIO environment type
 * @tparam Fault
 *   ZIO error type
 */
final case class ZioSystem[Environment, Fault](mapException: Throwable => Fault, mapError: Fault => Throwable)(
  implicit val runtime: Runtime[Environment]
) extends AsyncEffectSystem[({ type Effect[A] = ZIO[Environment, Fault, A] })#Effect] {

  override def evaluate[T](value: => T): ZIO[Environment, Fault, T] =
    ZIO.attempt(value).mapError(mapException)

  override def successful[T](value: T): ZIO[Environment, Fault, T] =
    ZIO.succeed(value)

  override def failed[T](exception: Throwable): ZIO[Environment, Fault, T] =
    ZIO.fail(exception).mapError(mapException)

  override def either[T](effect: => ZIO[Environment, Fault, T]): ZIO[Environment, Fault, Either[Throwable, T]] =
    effect.either.map(_.fold(error => Left(mapError(error)), value => Right(value)))

  override def flatMap[T, R](effect: ZIO[Environment, Fault, T])(
    function: T => ZIO[Environment, Fault, R]
  ): ZIO[Environment, Fault, R] =
    effect.flatMap(function)

  override def runAsync[T](effect: ZIO[Environment, Fault, T]): Unit = {
    implicit val trace: Trace = Trace.empty
    Unsafe.unsafe { implicit unsafe =>
      runtime.unsafe.fork(effect)
      ()
    }
  }

  override def completable[T]
    : ZIO[Environment, Fault, Completable[({ type Effect[A] = ZIO[Environment, Fault, A] })#Effect, T]] =
    map(Queue.dropping[Either[Fault, T]](1))(CompletableZIO.apply)

  sealed private case class CompletableZIO[T](private val queue: Queue[Either[Fault, T]])
    extends Completable[({ type Effect[A] = ZIO[Environment, Fault, A] })#Effect, T]() {

    override def effect: ZIO[Environment, Fault, T] =
      queue.take.flatMap {
        case Right(value) => successful(value)
        case Left(error) => failed(mapError(error))
      }

    override def succeed(value: T): ZIO[Environment, Fault, Unit] =
      map(queue.offer(Right(value)))(_ => ())

    override def fail(exception: Throwable): ZIO[Environment, Fault, Unit] =
      map(queue.offer(Left(mapException(exception))))(_ => ())
  }
}

object ZioSystem {

  /**
   * ZIO effect type with specified environment.
   *
   * @tparam T
   *   effectful value type
   * @tparam Environment
   *   ZIO environment type
   * @tparam Fault
   *   ZIO error type
   */
  type Effect[T, Fault, Environment] = ZIO[Environment, Fault, T]

  /**
   * Creates a ZIO effect system plugin with specific environment and with error handling using exceptions.
   *
   * @see
   *   [[https://zio.dev Library documentation]]
   * @see
   *   [[https://javadoc.io/doc/dev.zio/zio_3/latest/zio.html#RIO-0 Effect type]]
   * @param runtime
   *   ZIO runtime
   * @tparam Environment
   *   ZIO environment type
   * @return
   *   ZIO effect system plugin
   */
  def withRIO[Environment](implicit runtime: Runtime[Environment]): ZioSystem[Environment, Throwable] =
    ZioSystem(identity, identity)

  /**
   * Creates a ZIO effect system plugin without specific environment and with errors handling using exceptions.
   *
   * @see
   *   [[https://zio.dev Library documentation]]
   * @see
   *   [[https://javadoc.io/doc/dev.zio/zio_3/latest/zio.html#Task-0 Effect type]]
   * @param runtime
   *   ZIO runtime
   * @return
   *   ZIO effect system plugin
   */
  def withTask(implicit runtime: Runtime[Any] = Runtime.default): ZioSystem[Any, Throwable] =
    withRIO[Any]
}
