package automorph.spi

import automorph.spi.EffectSystem.Completable
import scala.concurrent.duration.FiniteDuration

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
   * Creates an effect by evaluating a potentially blocking and side-effecting value.
   *
   * Any exceptions thrown while computing the value result in a failed effect.
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
   * Creates a successfully completed effect out of an existing value without blocking or side-effects.
   *
   * The resulting effect cannot fail.
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
   * Creates a failed effect from an existing exception.
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
   * Creates a successfully completed effect by lifting an effect's errors into a value.
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
   * Creates an effect than suspends its execution for the specified duration.
   *
   * @param duration
   *   suspension duration
   * @return
   *   suspended effect
   */
  def sleep(duration: FiniteDuration): Effect[Unit]

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
  def runAsync[T](effect: => Effect[T]): Unit

  /**
   * Creates an externally completable effect.
   *
   * @tparam T
   *   effectful value type
   * @return
   *   completable effect
   */
  def completable[T]: Effect[Completable[Effect, T]]

  /**
   * Retries an effect specific number of times if it failed.
   *
   * If the effect failed after all the retries the resulting effect also fails.
   *
   * @param effect
   *   effectful value
   * @param retries
   *   maximum number of retries
   * @tparam T
   *   effectful value type
   * @return
   *   retried effect
   */
  def retry[T](effect: => Effect[T], retries: Int): Effect[T] = {
    require(retries >= 0, s"Invalid number of retries: $retries")
    attempt(effect, retries)
  }

  private def attempt[T](effect: => Effect[T], remaining: Int): Effect[T] = {
    flatFold(effect)(
      error => if (remaining <= 0) {
        failed(error)
      } else {
        attempt(effect, remaining - 1)
      },
      successful,
    )
  }
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
