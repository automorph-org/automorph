package test.transport.local

import automorph.spi.EffectSystem
import automorph.system.ScalazEffectSystem
import scala.concurrent.ExecutionContext.Implicits.global
import scalaz.effect.IO

class LocalScalazEffectTest extends LocalTest {

  type Effect[T] = IO[T]

  override lazy val system: EffectSystem[Effect] =
    ScalazEffectSystem()

  override def run[T](effect: Effect[T]): T =
    effect.unsafePerformIO()
}
