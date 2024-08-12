package test.transport.local

import automorph.spi.EffectSystem
import automorph.system.CatsEffectSystem
import cats.effect.IO
import cats.effect.unsafe.implicits.global
import test.transport.LocalTest

class LocalCatsEffectTest extends LocalTest {

  type Effect[T] = IO[T]

  override lazy val system: EffectSystem[Effect] =
    CatsEffectSystem()

  override def run[T](effect: Effect[T]): T =
    effect.unsafeRunSync()

  override def mandatory: Boolean =
    true
}
