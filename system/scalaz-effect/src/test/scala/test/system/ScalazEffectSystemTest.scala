package test.system

import automorph.spi.EffectSystem
import automorph.system.ScalazEffectSystem
import scalaz.effect.IO
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.Try

final class ScalazEffectSystemTest extends EffectSystemTest[IO] {

  lazy val system: EffectSystem[IO] = ScalazEffectSystem()

  def run[T](effect: => IO[T]): Either[Throwable, T] =
    Try(effect.unsafePerformIO()).toEither
}
