package test.core

import automorph.RpcException.{FunctionNotFound, InvalidArguments, InvalidResponse}
import automorph.spi.{EffectSystem, MessageCodec}
import automorph.{RpcClient, RpcServer}
import org.scalacheck.Arbitrary
import org.slf4j.{Logger, LoggerFactory}
import scala.util.{Failure, Success, Try}
import test.api.Generators.arbitraryRecord
import test.api.{ComplexApi, ComplexApiImpl, InvalidApi, Record, SimpleApi, SimpleApiImpl}
import test.base.BaseTest

/**
 * Main client -> server remote API function invocation test.
 *
 * Checks the results of remote RPC function invocations against identical local invocations.
 */
trait CoreTest extends BaseTest {

  /** Effect type. */
  type Effect[_]

  /** Request context type. */
  type Context
  type SimpleApiType = SimpleApi[Effect]
  type ComplexApiType = ComplexApi[Effect, Context]
  type InvalidApiType = InvalidApi[Effect]
  private type GenericServer[E[_], C] = RpcServer[Any, MessageCodec[Any], E, C]
  private type GenericClient[E[_], C] = RpcClient[Any, MessageCodec[Any], E, C]

  case class TestFixture(
    client: RpcClient[?, ?, Effect, Context],
    server: RpcServer[?, ?, Effect, Context],
    simpleApi: SimpleApiType,
    complexApi: ComplexApiType,
    invalidApi: InvalidApiType,
    call: (String, (String, String)) => Effect[String],
    tell: (String, (String, String)) => Effect[Unit],
  ) {

    val genericClient: GenericClient[Effect, Context] = client.asInstanceOf[GenericClient[Effect, Context]]
    val genericServer: GenericServer[Effect, Context] = server.asInstanceOf[GenericServer[Effect, Context]]

    def name: String = {
      val rpcProtocol = genericClient.rpcProtocol
      val codecName = rpcProtocol.messageCodec.getClass.getSimpleName.replaceAll("MessageCodec$", "")
      s"${rpcProtocol.name} / $codecName"
    }
  }

  val logger: Logger = LoggerFactory.getLogger(getClass)
  val simpleApi: SimpleApiType = SimpleApiImpl(system)
  val complexApi: ComplexApiType = ComplexApiImpl(system, arbitraryContext.arbitrary.sample.get)

  def system: EffectSystem[Effect]

  def run[T](effect: Effect[T]): T

  implicit def arbitraryContext: Arbitrary[Context]

  def fixtures: Seq[TestFixture]

  def integration: Boolean =
    false

  "" - {
    if (BaseTest.testSimple) {
      // Simple tests
      fixtures.foreach { fixture =>
        fixture.name - {
          "Basic" - {
            "Simple API" - {
              val apis = (fixture.simpleApi, simpleApi)
              "method" in {
                consistent(apis)(_.method("value")).shouldBe(true)
              }
            }
          }
        }
      }
    } else {
      if (BaseTest.testAll || !integration) {
        // All tests
        fixtures.foreach { fixture =>
          fixture.name - {
            "Static" - {
              "Simple API" - {
                val apis = (fixture.simpleApi, simpleApi)
                "method" in {
                  check { (a0: String) =>
                    consistent(apis)(_.method(a0))
                  }
                }
              }
              "Complex API" - {
                val apis = (fixture.complexApi, complexApi)
                "method0" in {
                  check { (_: Unit) =>
                    consistent(apis)(_.method0())
                  }
                }
                "method1" in {
                  check { (_: Unit) =>
                    consistent(apis)(_.method1())
                  }
                }
                "method2" in {
                  check { (a0: String) =>
                    consistent(apis)(_.method2(a0))
                  }
                }
                "method3" in {
                  check { (a0: Float, a1: Long, a2: Option[List[Int]]) =>
                    consistent(apis)(_.method3(a0, a1, a2))
                  }
                }
                "method4" in {
                  check { (a0: Double, a1: Byte, a2: Map[String, Int], a3: Option[String]) =>
                    consistent(apis)(_.method4(BigDecimal(a0), a1, a2, a3))
                  }
                }
                "method5" in {
                  check { (a0: Boolean, a1: Short, a2: List[Int]) =>
                    consistent(apis)(_.method5(a0, a1)(a2))
                  }
                }
                "method6" in {
                  check { (a0: Record, a1: Double) =>
                    consistent(apis)(_.method6(a0, a1))
                  }
                }
                "method7" in {
                  check { (a0: Record, a1: Boolean, context: Context) =>
                    implicit val usingContext: Context = context
                    consistent(apis)(_.method7(a0, a1))
                  }
                }
                "method8" in {
                  check { (a0: Record, a1: String, a2: Option[Double]) =>
                    consistent(apis) { api =>
                      system.map(api.method8(a0, a1, a2)) { result =>
                        s"${result.result} - ${result.context.getClass.getName}"
                      }
                    }
                  }
                }
                "method9" in {
                  check { (a0: String) =>
                    val (testedApi, referenceApi) = apis
                    val result = Try(run(testedApi.method9(a0))).toEither
                    val expected = Try(run(referenceApi.method9(a0))).toEither
                    val expectedErrorMessage = expected.swap.map(error =>
                      s"[${error.getClass.getSimpleName}] ${Option(error.getMessage).getOrElse("")}"
                    )
                    expectedErrorMessage == result.swap.map(_.getMessage)
                  }
                }
              }
              "Invalid API" - {
                val api = fixture.invalidApi
                "Function not found" in {
                  val error = intercept[FunctionNotFound](run(api.nomethod(""))).getMessage.toLowerCase
                  error.should(include("function not found"))
                  error.should(include("nomethod"))
                }
                "Optional arguments" in {
                  run(api.method3(0, Some(0)))
                }
                "Malformed argument" in {
                  val error = intercept[InvalidArguments] {
                    run(api.method4(BigDecimal(0), Some(true), None))
                  }.getMessage.toLowerCase
                  error.should(include("malformed argument"))
                  error.should(include("p1"))
                }
                "Missing arguments" in {
                  val error = intercept[InvalidArguments] {
                    run(api.method5(p0 = true, 0))
                  }.getMessage.toLowerCase
                  error.should(include("missing argument"))
                  error.should(include("p2"))
                }
                "Redundant arguments" in {
                  val error = intercept[InvalidArguments](run(api.method1(""))).getMessage.toLowerCase
                  error.should(include("redundant arguments"))
                  error.should(include("0"))
                }
                "Malformed result" in {
                  val error = intercept[InvalidResponse] {
                    run(api.method2(""))
                  }.getMessage.toLowerCase
                  error.should(include("malformed result"))
                }
              }
            }
            "Dynamic" - {
              "Simple API" - {
                "Call" in {
                  check { (a0: String) =>
                    val expected = run(simpleApi.method(a0))
                    execute("Dynamic / Simple API / Call", fixture.call("method", "argument" -> a0)) == expected
                  }
                }
                "Tell" in {
                  check { (a0: String) =>
                    execute("Dynamic / Simple API / Tell", fixture.tell("method", "argument" -> a0))
                    true
                  }
                }
                "Alias" in {
                  check { (a0: String) =>
                    val expected = run(simpleApi.method(a0))
                    execute("Dynamic / Simple API / Alias", fixture.call("function", "argument" -> a0)) == expected
                  }
                }
              }
            }
          }
        }
      }
    }
  }

  override def afterAll(): Unit = {
    fixtures.foreach(_.genericClient.close())
    super.afterAll()
  }

  private def execute[T](description: String, value: => Effect[T]): T =
    Try(run(value)) match {
      case Success(result) => result
      case Failure(error) =>
        logger.error(description, error)
        throw error
    }

  private def consistent[Api, Result](apis: (Api, Api))(function: Api => Effect[Result]): Boolean =
    Try {
      val (testedApi, referenceApi) = apis
      val result = run(function(testedApi))
      val expected = run(function(referenceApi))
      val outcome = expected == result
      if (!outcome) {
        logger.error(s"Actual API call result not equal to the expected result: $result != $expected")
      }
      outcome
    } match {
      case Success(result) => result
      case Failure(error) =>
        logger.error("API call failed", error)
        false
    }
}
