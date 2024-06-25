package test.system

import automorph.spi.EffectSystem
import scala.util.{Failure, Success, Try}
import test.base.BaseTest

/**
 * Effect system test.
 *
 * @tparam Effect effect type
 */
trait EffectSystemTest[Effect[_]] extends BaseTest {

  private val text = "test"
  private val number = 0
  private val exception = TestException(text)

  def system: EffectSystem[Effect]

  def run[T](effect: => Effect[T]): Either[Throwable, T]

  sealed case class TestException(message: String) extends RuntimeException(message)

  "" - {
    "Evaluate" - {
      "Success" in {
        val effect = system.evaluate(text)
        run(effect).shouldEqual(Right(text))
      }
      "Failure" in {
        run(system.evaluate(throw exception)).shouldEqual(Left(exception))
      }
    }
    "Successful" in {
      val effect = system.successful(text)
      run(effect).shouldEqual(Right(text))
    }
    "Failed" in {
      Try(system.failed(exception)) match {
        case Success(effect) => run(effect).shouldEqual(Left(exception))
        case Failure(error) => error.shouldEqual(exception)
      }
    }
    "Map" - {
      "Success" in {
        val effect = system.map(system.successful(text))(result => s"$result$number")
        run(effect).shouldEqual(Right(s"$text$number"))
      }
      "Failure" in {
        Try(system.map(system.failed(exception))(_: Unit => ())) match {
          case Success(effect) => run(effect).shouldEqual(Left(exception))
          case Failure(error) => error.shouldEqual(exception)
        }
      }
    }
    "FlatMap" - {
      "Success" in {
        val effect = system.flatMap(system.successful(text))(result => system.successful(s"$result$number"))
        run(effect).shouldEqual(Right(s"$text$number"))
      }
      "Failure" in {
        Try(system.flatMap(system.failed(exception))(_: Unit => system.successful {})) match {
          case Success(effect) => run(effect).shouldEqual(Left(exception))
          case Failure(error) => error.shouldEqual(exception)
        }
      }
    }
    "Either" - {
      "Success" in {
        val effect = system.either(system.successful(text))
        run(effect).shouldEqual(Right(Right(text)))
      }
      "Failure" in {
        Try(system.either(system.failed(exception))) match {
          case Success(effect) => run(effect).shouldEqual(Right(Left(exception)))
          case Failure(error) => error.shouldEqual(exception)
        }
      }
    }
    "RunAsync" in {
      system.runAsync(system.evaluate(text))
    }
  }
}
