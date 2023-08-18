package test.base

import java.util.concurrent.Semaphore
import test.base.Mutex.semaphore

trait Mutex {
  /**
   * Acquire mutual exclusion lock or block until it is available.
   */
  def lock(): Unit =
    semaphore.acquire()

  /**
   * Release mutual exclusion lock.
   */
  def unlock(): Unit =
    semaphore.release()
}

object Mutex {
  private val semaphore: Semaphore = new Semaphore(1)
}
