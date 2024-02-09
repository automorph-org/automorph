package test.transport.http

import automorph.spi.EffectSystem
import automorph.system.IdentitySystem
import automorph.system.IdentitySystem.Identity

class UrlClientHttpIdentityTest extends UrlClientHttpTest {

  type Effect[T] = Identity[T]

  override lazy val system: EffectSystem[Effect] = IdentitySystem()

  override def run[T](effect: Effect[T]): T =
    effect
}
