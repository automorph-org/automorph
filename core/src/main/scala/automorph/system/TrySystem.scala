package automorph.system

import automorph.spi.EffectSystem
import automorph.spi.EffectSystem.Completable
import java.util.concurrent.{ArrayBlockingQueue, BlockingQueue}
import scala.concurrent.duration.FiniteDuration
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

  override def fold[T, R](effect: => Try[T])(failure: Throwable => R, success: T => R): Try[R] =
    Success(effect.fold(failure, success))

  override def flatFold[T, R](effect: => Try[T])(failure: Throwable => Try[R], success: T => Try[R]): Try[R] =
    effect.fold(failure, success)

  override def map[T, R](effect: Try[T])(function: T => R): Try[R] =
    effect.map(function)

  override def flatMap[T, R](effect: Try[T])(function: T => Try[R]): Try[R] =
    effect.flatMap(function)

  override def sleep(duration: FiniteDuration): Try[Unit] =
    Success(Thread.sleep(duration.toMillis))

  override def runAsync[T](effect: => Try[T]): Unit = {
    effect
    ()
  }

  override def completable[T]: Try[EffectSystem.Completable[Try, T]] =
    Success(CompletableTry())

   sealed private case class CompletableTry[T]() extends Completable[Try, T]() {
     private val queue: BlockingQueue[Try[T]] = new ArrayBlockingQueue(1)

     override def effect: Try[T] =
       queue.take()

     override def succeed(value: T): Try[Unit] =
       Try(queue.put(Success(value)))

     override def fail(exception: Throwable): Try[Unit] =
       Try(queue.put(Failure(exception)))
   }
}

object TrySystem {

  /** Effect type. */
  type Effect[T] = Try[T]
}
