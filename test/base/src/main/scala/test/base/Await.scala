package test.base

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}

trait Await {

  /**
   * Wait for completion of the specified future.
   *
   * @param future
   *   future to complete
   * @tparam T
   *   future result type
   * @return
   *   future result
   */
  def await[T](future: => Future[T]): T =
    Await.result(future, Duration.Inf)
}
