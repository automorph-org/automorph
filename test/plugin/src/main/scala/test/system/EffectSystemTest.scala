package test.system

import automorph.spi.EffectSystem
import test.base.BaseTest
import java.util.concurrent.TimeUnit
import scala.concurrent.duration.FiniteDuration
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
  private val exception = TestException(text)
  private val delay = 20L

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
        Try(system.evaluate(throw exception)) match {
          case Success(effect) => run(effect).shouldEqual(Left(exception))
          case Failure(error) => error.shouldEqual(exception)
        }
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
        Try(system.map(system.failed[String](exception))(_ => ())) match {
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
        Try(system.flatMap(system.failed[String](exception))(_ => system.successful {})) match {
          case Success(effect) => run(effect).shouldEqual(Left(exception))
          case Failure(error) => error.shouldEqual(exception)
        }
      }
    }
    "Either" - {
      "Success" in {
        val effect = system.either(system.successful(true))
        run(effect).shouldEqual(Right(Right(true)))
      }
      "Failure" in {
        Try(system.either(system.failed[Boolean](exception))) match {
          case Success(effect) => run(effect).shouldEqual(Right(Left(exception)))
          case Failure(error) => error.shouldEqual(exception)
        }
      }
    }
    "Fold" - {
      "Success" in {
        val effect = system.fold(system.successful {})(_ => false, _ => true)
        run(effect).shouldEqual(Right(true))
      }
      "Failure" in {
        val effect = system.fold(system.failed[Boolean](exception))(_ => false, _ => true)
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
        val effect = system.flatFold(system.failed[Boolean](exception))(
          _ => system.successful(false),
          _ => system.successful(true),
        )
        run(effect).shouldEqual(Right(false))
      }
    }
    "RunAsync" in {
      var state = false
      system.runAsync(system.evaluate { state = true })
      Thread.sleep(delay)
      state.shouldEqual(true)
    }
    "Sleep" in {
      var state = false
      system.runAsync(system.map(system.sleep(FiniteDuration(delay, TimeUnit.MILLISECONDS)))(_ => state = true))
      Thread.sleep(delay * delay)
      state.shouldEqual(true)
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
          system.flatMap(completable.fail(exception))(_ => completable.effect)
        }
        run(effect).shouldEqual(Left(exception))
      }
    }
    "Retry" - {
      "Success" in {
        var retries = 3
        val effect = system.retry(
          {
            retries -= 1
            if (retries < 0) system.successful(text) else system.failed(exception)
          },
          retries,
        )
        run(effect).shouldEqual(Right(text))
      }
      "Failure" in {
        lazy val effect = system.retry(system.failed(exception), 3)
        run(effect).shouldEqual(Left(exception))
      }
    }
  }
}
