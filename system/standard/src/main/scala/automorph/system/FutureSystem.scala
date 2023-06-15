package automorph.system

import automorph.spi.AsyncEffectSystem
import automorph.spi.AsyncEffectSystem.Completable
import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util.Success

/**
 * Asynchronous effect system plugin using Future as an effect type.
 *
 * @see
 *   [[https://docs.scala-lang.org/overviews/core/futures.html Library documentation]]
 * @see
 *   [[https://www.scala-lang.org/api/current/scala/concurrent/Future.html Effect type]]
 * @constructor
 *   Creates an asynchronous effect system plugin using `Future` as an effect type.
 * @param executionContext
 *   execution context
 */
final case class FutureSystem()(implicit val executionContext: ExecutionContext)
  extends AsyncEffectSystem[Future] {

  override def evaluate[T](value: => T): Future[T] =
    Future(value)

  override def successful[T](value: T): Future[T] =
    Future.successful(value)

  override def failed[T](exception: Throwable): Future[T] =
    Future.failed(exception)

  override def either[T](effect: => Future[T]): Future[Either[Throwable, T]] =
    effect.transform(value => Success(value.toEither))

  override def flatMap[T, R](effect: Future[T])(function: T => Future[R]): Future[R] =
    effect.flatMap(function)

  override def runAsync[T](effect: Future[T]): Unit =
    ()

  override def completable[T]: Future[Completable[Future, T]] =
    Future.successful(CompletableFuture())

  private sealed case class CompletableFuture[T]() extends Completable[Future, T]() {
    private val promise: Promise[T] = Promise()

    override def effect: Future[T] =
      promise.future

    override def succeed(value: T): Future[Unit] =
      Future(promise.success(value))

    override def fail(exception: Throwable): Future[Unit] =
      Future(promise.failure(exception))
  }
}

case object FutureSystem {

  /**
   * Effect type.
   *
   * @tparam T
   *   value type
   */
  type Effect[T] = Future[T]
}
