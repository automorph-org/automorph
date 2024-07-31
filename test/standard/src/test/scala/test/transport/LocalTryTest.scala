package test.transport.local

import automorph.spi.EffectSystem
import automorph.system.TrySystem
import test.transport.LocalTest
import scala.util.Try

final class LocalTryTest extends LocalTest {

  type Effect[T] = Try[T]

  override lazy val system: EffectSystem[Effect] = TrySystem()

  override def run[T](effect: Effect[T]): T =
    effect.get

  override def mandatory: Boolean =
    true
}
