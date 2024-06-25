package automorph.system

import automorph.spi.EffectSystem
import automorph.spi.EffectSystem.Completable
import automorph.system.IdentitySystem.Identity
import java.util.concurrent.{ArrayBlockingQueue, BlockingQueue}
import scala.concurrent.duration.FiniteDuration
import scala.util.{Failure, Success, Try}

/**
 * Synchronous effect system plugin using identity as an effect type.
 *
 * Represents direct use of computed values without wrapping them in an effect.
 *
 * @see
 *   [[https://www.scala-lang.org/files/archive/api/3.x/ documentation]]
 * @see
 *   [[https://scala-lang.org/api/3.x/scala/Predef$.html#identity-957 Effect type]]
 * @constructor
 *   Creates a synchronous effect system plugin using identity as an effect type.
 */
final case class IdentitySystem() extends EffectSystem[Identity] {

  override def evaluate[T](value: => T): T =
    value

  override def successful[T](value: T): T =
    value

  override def failed[T](exception: Throwable): T =
    throw exception

  override def either[T](effect: => T): Either[Throwable, T] =
    Try(effect).toEither

  override def fold[T, R](effect: => Identity[T])(failure: Throwable => R, success: T => R): Identity[R] =
    Try(effect).toEither.fold(failure, success)

  override def flatFold[T, R](effect: => Identity[T])(
    failure: Throwable => Identity[R],
    success: T => Identity[R],
  ): Identity[R] =
    Try(effect).toEither.fold(failure, success)

  override def map[T, R](effect: Identity[T])(function: T => R): Identity[R] =
    function(effect)

  override def flatMap[T, R](effect: T)(function: T => R): R =
    function(effect)

  override def sleep(duration: FiniteDuration): Unit =
    Thread.sleep(duration.toMillis)

  override def runAsync[T](effect: => T): Unit = {
    effect
    ()
  }

  override def completable[T]: Identity[EffectSystem.Completable[Identity, T]] =
    CompletableIdentity()

   sealed private case class CompletableIdentity[T]() extends Completable[Identity, T]() {
     private val queue: BlockingQueue[Try[T]] = new ArrayBlockingQueue(1)

     override def effect: Identity[T] =
       queue.take().get

     override def succeed(value: T): Unit =
       queue.put(Success(value))

     override def fail(exception: Throwable): Unit = {
       queue.put(Failure(exception))
     }
   }
}

object IdentitySystem {

  /** Effect type. */
  type Effect[T] = Identity[T]

  /** Identity type. */
  type Identity[T] = T
}
