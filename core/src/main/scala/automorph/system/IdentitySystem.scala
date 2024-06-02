package automorph.system

import automorph.spi.EffectSystem
import automorph.spi.EffectSystem.Completable
import automorph.system.IdentitySystem.Identity
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Promise}
import scala.util.Try

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

  override def runAsync[T](effect: => T): Unit = {
    effect
    ()
  }

  override def completable[T]: Identity[EffectSystem.Completable[Identity, T]] =
    CompletableFuture()

  sealed private case class CompletableFuture[T]() extends Completable[Identity, T]() {
    private val promise: Promise[T] = Promise()

    override def effect: T =
      Await.result(promise.future, Duration.Inf)

    override def succeed(value: T): Unit =
      promise.success(value)

    override def fail(exception: Throwable): Unit =
      promise.failure(exception)
  }
}

object IdentitySystem {

  /** Effect type. */
  type Effect[T] = Identity[T]

  /** Identity type. */
  type Identity[T] = T
}
