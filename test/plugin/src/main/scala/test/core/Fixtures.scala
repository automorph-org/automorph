package test.core

import automorph.{RpcClient, RpcServer}
import automorph.schema.OpenApi
import automorph.spi.MessageCodec
import test.api.{ComplexApi, InvalidApi, SimpleApi}

object Fixtures {

  final case class Apis[Effect[_], Context](
    simpleApi: SimpleApi[Effect],
    complexApi: ComplexApi[Effect, Context],
    invalidApi: InvalidApi[Effect],
  )

  final case class Functions[Effect[_]](
    callOpenApi: String => Effect[OpenApi],
    callString: (String, (String, String)) => Effect[String],
    tell: (String, (String, String)) => Effect[Unit],
  )

  final case class Fixture[Effect[_], Context](
    id: String,
    client: RpcClient[?, ?, Effect, Context],
    server: RpcServer[?, ?, Effect, Context],
    apis: Apis[Effect, Context],
    functions: Functions[Effect],
  ) {
    private type GenericServer[E[_], C] = RpcServer[Any, MessageCodec[Any], E, C]
    private type GenericClient[E[_], C] = RpcClient[Any, MessageCodec[Any], E, C]

    val genericClient: GenericClient[Effect, Context] = client.asInstanceOf[GenericClient[Effect, Context]]
    val genericServer: GenericServer[Effect, Context] = server.asInstanceOf[GenericServer[Effect, Context]]
  }

  /** Test level environment variable. */
  private val testLevelEnvironment = "TEST_LEVEL"

  /** Simple tests environment value. */
  private val testSimpleValue = "complex"

  /** Complex tests environment value. */
  private val testComplexValue = "complex"

  /** All tests environment value. */
  private val testAllValue = "all"

  /** Execute simple standard remote API tests for all transport plugins and default codec plugin only. */
  def simple: Boolean =
    complex || Option(System.getenv(testLevelEnvironment)).exists(_.toLowerCase == testSimpleValue)

  /** Execute complex generative remote API tests for all transport plugins and default codec plugin only. */
  def complex: Boolean =
    all || Option(System.getenv(testLevelEnvironment)).exists(_.toLowerCase == testComplexValue)

  /** Execute complex generative remote API tests for all transport plugins and all codec plugins. */
  def all: Boolean =
    Option(System.getenv(testLevelEnvironment)).exists(_.toLowerCase == testAllValue)
}
