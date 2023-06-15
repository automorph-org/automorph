package test.system

import automorph.spi.EffectSystem
import automorph.system.IdentitySystem
import automorph.system.IdentitySystem.Identity
import scala.util.Try

class IdentityTest extends EffectSystemTest[Identity] {

  lazy val system: EffectSystem[Identity] = IdentitySystem()

  def run[T](effect: Identity[T]): Either[Throwable, T] =
    Try(effect).toEither
}
