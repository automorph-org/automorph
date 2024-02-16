package test.system

import automorph.system.FutureSystem
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.Try
import test.base.Await

class FutureTest extends AsyncEffectSystemTest[Future] with Await {

  lazy val system: FutureSystem = FutureSystem()

  def run[T](effect: Future[T]): Either[Throwable, T] =
    Try(await(effect)).toEither
}
