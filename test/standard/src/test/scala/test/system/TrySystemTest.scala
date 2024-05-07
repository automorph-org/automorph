package test.system

import automorph.spi.EffectSystem
import automorph.system.TrySystem
import scala.util.Try

final class TrySystemTest extends EffectSystemTest[Try] {

  lazy val system: EffectSystem[Try] = TrySystem()

  def run[T](effect: => Try[T]): Either[Throwable, T] =
    effect.toEither
}
