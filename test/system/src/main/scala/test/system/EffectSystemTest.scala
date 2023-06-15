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

  sealed case class TestException(message: String) extends RuntimeException(message)

  val text = "test"
  val number = 0
  val error: TestException = TestException(text)

  def system: EffectSystem[Effect]

  def run[T](effect: Effect[T]): Either[Throwable, T]

  "" - {
    "Evaluate" - {
      "Success" in {
        val effect = system.evaluate(text)
        run(effect).shouldEqual(Right(text))
      }
      "Failure" in {
        Try(system.evaluate(throw error)) match {
          case Success(effect) => run(effect).shouldEqual(Left(error))
          case Failure(error) => error.shouldEqual(error)
        }
      }
    }
    "Successful" in {
      val effect = system.successful(text)
      run(effect).shouldEqual(Right(text))
    }
    "Failed" in {
      Try(system.failed(error)) match {
        case Success(effect) => run(effect).shouldEqual(Left(error))
        case Failure(error) => error.shouldEqual(error)
      }
    }
    "Map" - {
      "Success" in {
        val effect = system.map(system.successful(text))(result => s"$result$number")
        run(effect).shouldEqual(Right(s"$text$number"))
      }
      "Failure" in {
        Try(system.map(system.failed(error))(_ => ())) match {
          case Success(effect) => run(effect).shouldEqual(Left(error))
          case Failure(error) => error.shouldEqual(error)
        }
      }
    }
    "FlatMap" - {
      "Success" in {
        val effect = system.flatMap(system.successful(text))(result => system.successful(s"$result$number"))
        run(effect).shouldEqual(Right(s"$text$number"))
      }
      "Failure" in {
        Try(system.flatMap(system.failed(error))(_ => system.successful {})) match {
          case Success(effect) => run(effect).shouldEqual(Left(error))
          case Failure(error) => error.shouldEqual(error)
        }
      }
    }
    "Either" - {
      "Success" in {
        val effect = system.either(system.successful(text))
        run(effect).shouldEqual(Right(Right(text)))
      }
      "Failure" in {
        Try(system.either(system.failed(error))) match {
          case Success(effect) => run(effect).shouldEqual(Right(Left(error)))
          case Failure(error) => error.shouldEqual(error)
        }
      }
    }
    "RunAsync" in {
      system.runAsync(system.evaluate(text))
    }
  }
}
