package test.system

import automorph.system.ZioSystem
import zio.{Runtime, Task, Unsafe}

class ZioTest extends AsyncEffectSystemTest[Task] {
  override lazy val system: ZioSystem[Throwable] = {
    implicit val runtime: Runtime[Any] = Runtime.default
    ZioSystem()
  }

  def run[T](effect: Task[T]): Either[Throwable, T] =
    Unsafe.unsafe { implicit unsafe =>
      system.runtime.unsafe.run(effect).toEither.swap.map(_.getCause).swap
    }
}
