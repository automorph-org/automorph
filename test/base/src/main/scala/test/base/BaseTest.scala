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

case object BaseTest {

  /** Test level environment variable. */
  private val testLevelEnvironment = "TEST_LEVEL"
  /** Execute simple remote API tests */
  private val testSimpleValue = "simple"
  /** Execute all remote API tests */
  private val testAllValue = "all"

  /** Execute simple remote API tests */
  final def testSimple: Boolean =
    Option(System.getenv(testLevelEnvironment)).exists(_.toLowerCase == testSimpleValue)

  /** Execute all remote API tests */
  final def testAll: Boolean =
    Option(System.getenv(testLevelEnvironment)).exists(_.toLowerCase == testAllValue)
}
