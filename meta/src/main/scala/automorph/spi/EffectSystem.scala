package automorph.spi

import automorph.spi.EffectSystem.Completable

/**
 * Computational effect system plugin.
 *
 * Enables remote APIs to use specific effect handling abstraction.
 *
 * The underlying runtime must support monadic composition of effectful values.
 *
 * @tparam Effect
 *   effect type (similar concept to IO Monad in Haskell)
 */
trait EffectSystem[Effect[_]] {

  /**
   * Lifts a potentially blocking and side-effecting value into a new effect of specified type.
   *
   * Exceptions thrown while computing the value are translated into a failed effect.
   *
   * @param value
   *   side-effecting value
   * @tparam T
   *   effectful value type
   * @return
   *   effect containing the value
   */
  def evaluate[T](value: => T): Effect[T]

  /**
   * Lifts a value without blocking or side-effects into a successfully completed effect of specified type.
   *
   * @param value
   *   value
   * @tparam T
   *   effectful value type
   * @return
   *   effect containing the value
   */
  def successful[T](value: T): Effect[T]

  /**
   * Lifts a exception into a failed effect of specified type.
   *
   * @param exception
   *   exception
   * @tparam T
   *   effectful value type
   * @return
   *   effect containing the exception
   */
  def failed[T](exception: Throwable): Effect[T]

  /**
   * Creates a new effect by lifting an effect's errors into a value.
   *
   * The resulting effect cannot fail.
   *
   * @param effect
   *   effectful value
   * @tparam T
   *   effectful value type
   * @return
   *   effectful error or the original value
   */
  def either[T](effect: => Effect[T]): Effect[Either[Throwable, T]]

  /**
   * Creates a new effect by applying `onFailure` if an effect failed or `onSuccess` if an effect succeeded.
   *
   * @param effect
   *   effectful value
   * @param onFailure
   *   function applied if the effect failed
   * @param onSuccess
   *   function applied if the effect succeded
   * @tparam T
   *   effectful value type
   * @tparam R
   *   function result type
   * @return
   *   transformed effectful value
   */
  def fold[T, R](effect: => Effect[T])(onFailure: Throwable => R, onSuccess: T => R): Effect[R]

  /**
   * Creates a new effect by applying `onFailure` if an effect failed or `onSuccess` if an effect succeeded.
   *
   * @param effect
   *   effectful value
   * @param onFailure
   *   effectful function applied if the effect failed
   * @param onSuccess
   *   effectul function applied if the effect succeded
   * @tparam T
   *   effectful value type
   * @tparam R
   *   function result type
   * @return
   *   transformed effectful value
   */
  def flatFold[T, R](effect: => Effect[T])(onFailure: Throwable => Effect[R], onSuccess: T => Effect[R]): Effect[R]

  /**
   * Creates a new effect by applying a function to an effect's value.
   *
   * @param effect
   *   effectful value
   * @param function
   *   function applied to the specified effect's value
   * @tparam T
   *   effectful value type
   * @tparam R
   *   function result type
   * @return
   *   transformed effectful value
   */
  def map[T, R](effect: Effect[T])(function: T => R): Effect[R]

  /**
   * Creates a new effect by applying an effectful function to an effect's value.
   *
   * @param effect
   *   effectful value
   * @param function
   *   effectful function applied to the specified effect's value
   * @tparam T
   *   effectful value type
   * @tparam R
   *   effectful function result type
   * @return
   *   effect containing the transformed value
   */
  def flatMap[T, R](effect: Effect[T])(function: T => Effect[R]): Effect[R]

  /**
   * Executes an effect asynchronously without blocking and discard the result.
   *
   * @param effect
   *   effectful value
   * @tparam T
   *   effectful value type
   * @return
   *   nothing
   */
  def runAsync[T](effect: Effect[T]): Unit

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

object EffectSystem {

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
     * Effect containing an externally supplied result value or an error.
     *
     * @return
     *   effect containing an externally supplied result value or an error
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
     * Completes the effect with an error.
     *
     * @param exception
     *   exception
     * @return
     *   nothing
     */
    def fail(exception: Throwable): Effect[Unit]
  }
}
