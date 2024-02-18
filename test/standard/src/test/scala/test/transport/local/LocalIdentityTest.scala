package test.transport.local

import automorph.system.IdentitySystem
import automorph.system.IdentitySystem.Identity
import automorph.spi.EffectSystem
import test.transport.LocalTest

final class LocalIdentityTest extends LocalTest {

  type Effect[T] = Identity[T]

  override lazy val system: EffectSystem[Effect] = IdentitySystem()

  override def run[T](effect: Effect[T]): T =
    effect

  override def basic: Boolean =
    true
}
