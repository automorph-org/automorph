package test.system

import automorph.system.CatsEffectSystem
import cats.effect.IO
import cats.effect.unsafe.implicits.global
import scala.util.Try

class CatsEffectTest extends AsyncEffectSystemTest[IO] {

  lazy val system: CatsEffectSystem = CatsEffectSystem()

  def run[T](effect: IO[T]): Either[Throwable, T] =
    Try(effect.unsafeRunSync()).toEither
}
