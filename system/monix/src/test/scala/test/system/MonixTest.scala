package test.system

import automorph.system.MonixSystem
import monix.eval.Task
import monix.execution.Scheduler.Implicits.global
import scala.concurrent.duration.Duration
import scala.util.Try

class MonixTest extends AsyncEffectSystemTest[Task] {

  lazy val system: MonixSystem = MonixSystem()

  def run[T](effect: Task[T]): Either[Throwable, T] =
    Try(effect.runSyncUnsafe(Duration.Inf)).toEither
}
