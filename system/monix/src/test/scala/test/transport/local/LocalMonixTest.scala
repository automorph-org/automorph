package test.transport.local

import automorph.spi.EffectSystem
import automorph.system.MonixSystem
import monix.eval.Task
import scala.concurrent.duration.Duration

class LocalMonixTest extends LocalTest {

  type Effect[T] = Task[T]

  override lazy val system: EffectSystem[Effect] =
    MonixSystem()

  override def run[T](effect: Effect[T]): T =
    effect.runSyncUnsafe(Duration.Inf)

  override def basic: Boolean =
    true
}
