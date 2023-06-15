package automorph.system

import automorph.spi.EffectSystem
import automorph.system.IdentitySystem.Identity
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

  override def flatMap[T, R](effect: T)(function: T => R): R =
    function(effect)

  override def runAsync[T](effect: T): Unit =
    ()
}

case object IdentitySystem {

  /**
   * Effect type.
   *
   * @tparam T
   *   value type
   */
  type Effect[T] = Identity[T]

  /**
   * Identity type.
   *
   * @tparam T
   *   value type
   */
  type Identity[T] = T
}
