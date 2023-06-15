package test.system

import automorph.system.ZioSystem
import zio.{Task, Unsafe}

class ZioTest extends AsyncEffectSystemTest[Task] {

  lazy val system: ZioSystem[Any] = ZioSystem.default

  def run[T](effect: Task[T]): Either[Throwable, T] =
    Unsafe.unsafe { implicit unsafe =>
      system.runtime.unsafe.run(effect).toEither.swap.map(_.getCause).swap
    }
}
