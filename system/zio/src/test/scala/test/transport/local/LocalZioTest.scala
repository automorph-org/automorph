package test.transport.local

import automorph.spi.EffectSystem
import automorph.system.ZioSystem
import zio.{Task, Unsafe}

class LocalZioTest extends LocalTest {

  type Effect[T] = Task[T]

  override lazy val system: EffectSystem[Effect] =
    ZioSystem[Any]()(ZioSystem.defaultRuntime)

  override def run[T](effect: Effect[T]): T =
    Unsafe.unsafe { implicit unsafe =>
      ZioSystem.defaultRuntime.unsafe.run(effect).toEither.swap.map(_.getCause).swap.toTry.get
    }
}
