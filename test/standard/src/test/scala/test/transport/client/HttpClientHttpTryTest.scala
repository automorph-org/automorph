package test.transport.client

import automorph.spi.EffectSystem
import automorph.system.TrySystem
import scala.util.Try

final class HttpClientHttpTryTest extends HttpClientHttpTest {

  type Effect[T] = Try[T]

  override lazy val system: EffectSystem[Effect] = TrySystem()

  override def run[T](effect: Effect[T]): T =
    effect.get

  override def basic: Boolean =
    true
}
