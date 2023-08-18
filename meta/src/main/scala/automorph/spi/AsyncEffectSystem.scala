package automorph.spi

import automorph.spi.AsyncEffectSystem.Completable

/**
 * Asynchronous computational effect system plugin.
 *
 * The underlying runtime must support monadic composition of effectful values
 * and creation of externally completable effects.
 *
 * @tparam Effect
 *   effect type (similar to IO Monad in Haskell)
 */
trait AsyncEffectSystem[Effect[_]] extends EffectSystem[Effect] {

  /**
   * Creates an externally completable effect.
   *
   * @tparam T
   *   effectful value type
   * @return
   *   completable effect
   */
  def completable[T]: Effect[Completable[Effect, T]]
}

object AsyncEffectSystem {

  /**
   * Externally completable effect.
   *
   * @tparam Effect
   *   effect type
   * @tparam T
   *   effectful value type
   */
  trait Completable[Effect[_], T] {
    /**
     * Effect containing an externally supplied result value or an exception.
     *
     * @return effect containing an externally supplied result value or an exception
     */
    def effect: Effect[T]

    /**
     * Completes the effect with a result value.
     *
     * @param value
     *   result value
     * @return
     *   nothing
     */
    def succeed(value: T): Effect[Unit]

    /**
     * Completes the effect with an exception.
     *
     * @param exception
     *   exception
     * @return
     *   nothing
     */
    def fail(exception: Throwable): Effect[Unit]
  }
}
