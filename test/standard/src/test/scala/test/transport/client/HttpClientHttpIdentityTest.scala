package test.transport.client

import automorph.spi.EffectSystem
import automorph.system.IdentitySystem
import automorph.system.IdentitySystem.Identity

final class HttpClientHttpIdentityTest extends HttpClientHttpTest {

  type Effect[T] = Identity[T]

  override lazy val system: EffectSystem[Effect] = IdentitySystem()

  override def run[T](effect: Effect[T]): T =
    effect

  override def mandatory: Boolean =
    true
}
