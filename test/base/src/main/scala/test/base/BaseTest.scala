package test.base

import org.scalatest.freespec.AnyFreeSpecLike
import org.scalatest.matchers.should.Matchers
import org.scalatest.{AppendedClues, BeforeAndAfterAll, BeforeAndAfterEach, OptionValues}
import org.scalatestplus.scalacheck.{Checkers, ScalaCheckPropertyChecks}

/**
 * Base structured test.
 *
 * Included functionality:
 *   - optional values retrieval support
 *   - before and after test hooks
 *   - result assertion matchers
 *   - additional test clues
 *   - property-based checks
 */
trait BaseTest
  extends AnyFreeSpecLike
  with OptionValues
  with BeforeAndAfterEach
  with BeforeAndAfterAll
  with Matchers
  with AppendedClues
  with Checkers
  with ScalaCheckPropertyChecks

object BaseTest {

  /** Test level environment variable. */
  private val testLevelEnvironment = "TEST_LEVEL"
  /** Standard tests environment value. */
  private val testStandardValue = "standard"
  /** Generative tests environment value. */
  private val testGenerativeValue = "generative"
  /** All tests environment value. */
  private val testAllValue = "all"

  /** Execute simple standard remote API tests for all transport plugins and default codec plugin only. */
  final def testStandard: Boolean =
    testGenerative || Option(System.getenv(testLevelEnvironment)).exists(_.toLowerCase == testStandardValue)

  /** Execute complex generative remote API tests for all transport plugins and default codec plugin only. */
  final def testGenerative: Boolean =
    testAll || Option(System.getenv(testLevelEnvironment)).exists(_.toLowerCase == testGenerativeValue)

  /** Execute complex generative remote API tests for all transport plugins and all codec plugins. */
  final def testAll: Boolean =
    Option(System.getenv(testLevelEnvironment)).exists(_.toLowerCase == testAllValue)
}
