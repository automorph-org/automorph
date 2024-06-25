package automorph.system

import automorph.spi.EffectSystem
import automorph.spi.EffectSystem.Completable
import java.util.{Timer, TimerTask}
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util.{Failure, Success}

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
final case class FutureSystem()(implicit val executionContext: ExecutionContext) extends EffectSystem[Future] {

  override def evaluate[T](value: => T): Future[T] =
    Future(value)

  override def successful[T](value: T): Future[T] =
    Future.successful(value)

  override def failed[T](exception: Throwable): Future[T] =
    Future.failed(exception)

  override def either[T](effect: => Future[T]): Future[Either[Throwable, T]] =
    effect.transform(value => Success(value.toEither))

  override def fold[T, R](effect: => Future[T])(failure: Throwable => R, success: T => R): Future[R] =
    effect.transform {
      case Success(value) => Success(success(value))
      case Failure(error) => Success(failure(error))
    }

  override def flatFold[T, R](effect: => Future[T])(failure: Throwable => Future[R], success: T => Future[R]): Future[R] =
    effect.transformWith {
      case Success(value) => success(value)
      case Failure(error) => failure(error)
    }

  override def map[T, R](effect: Future[T])(function: T => R): Future[R] =
    effect.map(function)

  override def flatMap[T, R](effect: Future[T])(function: T => Future[R]): Future[R] =
    effect.flatMap(function)

  override def sleep(duration: FiniteDuration): Future[Unit] = {
    val promise = Promise[Unit]()
    (new Timer).schedule(SleepTask(promise), duration.toMillis)
    promise.future
  }

  override def runAsync[T](effect: => Future[T]): Unit = {
    effect
    ()
  }

  override def completable[T]: Future[Completable[Future, T]] =
    Future.successful(CompletableFuture())

  sealed private case class CompletableFuture[T]() extends Completable[Future, T]() {
    private val promise: Promise[T] = Promise()

    override def effect: Future[T] =
      promise.future

    override def succeed(value: T): Future[Unit] =
      Future(promise.success(value))

    override def fail(exception: Throwable): Future[Unit] =
      Future(promise.failure(exception))
  }

  sealed private case class SleepTask(promise: Promise[Unit]) extends TimerTask {

    override def run(): Unit =
      promise.success(())
  }
}

object FutureSystem {

  /** Effect type. */
  type Effect[T] = Future[T]
}
