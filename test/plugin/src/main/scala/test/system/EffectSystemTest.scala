package test.system

import automorph.spi.EffectSystem
import test.base.BaseTest
import scala.util.{Failure, Success, Try}

/**
 * Effect system test.
 *
 * @tparam Effect
 *   effect type
 */
@scala.annotation.nowarn("msg=dead code")
trait EffectSystemTest[Effect[_]] extends BaseTest {

  sealed case class TestException(message: String) extends RuntimeException(message)

  private val text = "test"
  private val number = 0
  private val error = TestException(text)

  def system: EffectSystem[Effect]

  def run[T](effect: => Effect[T]): Either[Throwable, T]

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
    "Completable" - {
      "Success" in {
        val effect = system.flatMap(system.completable[String]) { completable =>
          system.flatMap(completable.succeed(text))(_ => completable.effect)
        }
        run(effect).shouldEqual(Right(text))
      }
      "Failure" in {
        lazy val effect = system.flatMap(system.completable[String]) { completable =>
          system.flatMap(completable.fail(error))(_ => completable.effect)
        }
        run(effect).shouldEqual(Left(error))
      }
    }
  }
}
