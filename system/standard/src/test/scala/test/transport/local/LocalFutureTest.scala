package test.transport.local

import automorph.spi.EffectSystem
import automorph.system.FutureSystem
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class LocalFutureTest extends LocalTest {

  type Effect[T] = Future[T]

  override lazy val system: EffectSystem[Effect] = FutureSystem()

  override def run[T](effect: Effect[T]): T =
    await(effect)
}
