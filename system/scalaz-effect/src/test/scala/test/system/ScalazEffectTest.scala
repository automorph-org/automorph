package test.system

import automorph.spi.EffectSystem
import automorph.system.ScalazEffectSystem
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.Try
import scalaz.effect.IO

class ScalazEffectTest extends EffectSystemTest[IO] {

  lazy val system: EffectSystem[IO] = ScalazEffectSystem()

  def run[T](effect: IO[T]): Either[Throwable, T] =
    Try(effect.unsafePerformIO()).toEither
}
