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
  /** Simple tests environment value. */
  private val testSimpleValue = "simple"
  /** Complex tests environment value. */
  private val testComplexValue = "complex"
  /** All tests environment value. */
  private val testAllValue = "all"

  /** Execute simple remote API tests for all transport plugins and default codec plugin only. */
  final def testSimple: Boolean =
    Option(System.getenv(testLevelEnvironment)).exists(_.toLowerCase == testSimpleValue)

  /** Execute complex remote API tests for all transport plugins and default codec plugin only. */
  final def testComplex: Boolean =
    Option(System.getenv(testLevelEnvironment)).exists(_.toLowerCase == testComplexValue)

  /** Execute complex remote API tests for all transport plugins and all codec plugins. */
  final def testAll: Boolean =
    Option(System.getenv(testLevelEnvironment)).exists(_.toLowerCase == testAllValue)
}
