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
trait EffectSystemTest[Effect[_]] extends BaseTest {

  private val text = "test"
  private val number = 0
  private val error = TestException(text)

  def system: EffectSystem[Effect]

  def run[T](effect: => Effect[T]): Either[Throwable, T]

  sealed private case class TestException(message: String) extends RuntimeException(message)

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
        Try(system.map(system.failed[String](error))(_ => ())) match {
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
        Try(system.flatMap(system.failed[String](error))(_ => system.successful {})) match {
          case Success(effect) => run(effect).shouldEqual(Left(error))
          case Failure(error) => error.shouldEqual(error)
        }
      }
    }
    "Either" - {
      "Success" in {
        val effect = system.either(system.successful(true))
        run(effect).shouldEqual(Right(Right(true)))
      }
      "Failure" in {
        Try(system.either(system.failed[Boolean](error))) match {
          case Success(effect) => run(effect).shouldEqual(Right(Left(error)))
          case Failure(error) => error.shouldEqual(error)
        }
      }
    }
    "Fold" - {
      "Success" in {
        val effect = system.fold(system.successful {})(_ => false, _ => true)
        run(effect).shouldEqual(Right(true))
      }
      "Failure" in {
        val effect = system.fold(system.failed[Boolean](error))(_ => false, _ => true)
        run(effect).shouldEqual(Right(false))
      }
    }
    "FlatFold" - {
      "Success" in {
        val effect = system.flatFold(system.successful {})(
          _ => system.successful(false),
          _ => system.successful(true),
        )
        run(effect).shouldEqual(Right(true))
      }
      "Failure" in {
        val effect = system.flatFold(system.failed[Boolean](error))(
          _ => system.successful(false),
          _ => system.successful(true),
        )
        run(effect).shouldEqual(Right(false))
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
    "Retry" - {
      "Success" in {
        var retries = 3
        val effect = system.retry(
          {
            retries -= 1
            if (retries < 0) system.successful(text) else system.failed(error)
          },
          retries,
        )
        run(effect).shouldEqual(Right(text))
      }
      "Failure" in {
        val effect = system.retry(system.failed(error), 3)
        run(effect).shouldEqual(Left(error))
      }
    }
  }
}
