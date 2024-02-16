package test.transport.local

import automorph.spi.EffectSystem
import automorph.system.ZioSystem
import zio.{Task, Unsafe}

class LocalZioTest extends LocalTest {
  type Effect[T] = Task[T]
  override lazy val system: EffectSystem[Effect] = zioSystem
  private lazy val zioSystem: ZioSystem[Any, Throwable] = ZioSystem.withTask

  override def run[T](effect: Effect[T]): T =
    Unsafe.unsafe { implicit unsafe =>
      zioSystem.runtime.unsafe.run(effect).toEither.swap.map(_.getCause).swap.toTry.get
    }

  override def basic: Boolean =
    true
}
