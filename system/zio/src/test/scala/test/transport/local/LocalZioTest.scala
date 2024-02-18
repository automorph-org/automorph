package test.transport.local

import automorph.system.ZioSystem
import zio.{Runtime, Task, Unsafe}

final class LocalZioTest extends LocalTest {
  type Effect[T] = Task[T]

  override lazy val system: ZioSystem[Throwable] = {
    implicit val runtime: Runtime[Any] = Runtime.default
    ZioSystem()
  }

  override def run[T](effect: Effect[T]): T =
    Unsafe.unsafe { implicit unsafe =>
      system.runtime.unsafe.run(effect).toEither.swap.map(_.getCause).swap.toTry.get
    }

  override def basic: Boolean =
    true
}
