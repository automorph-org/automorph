package test.transport.local

import automorph.system.TrySystem
import automorph.spi.EffectSystem
import scala.util.Try

class LocalTryTest extends LocalTest {

  type Effect[T] = Try[T]

  override lazy val system: EffectSystem[Effect] = TrySystem()

  override def run[T](effect: Effect[T]): T =
    effect.get
}
