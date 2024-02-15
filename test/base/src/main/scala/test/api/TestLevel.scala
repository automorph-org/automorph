package test.api

object TestLevel {

  /** Test level environment variable. */
  private val environment = "TEST_LEVEL"

  /** Simple tests environment value. */
  private val simpleValue = "complex"

  /** Complex tests environment value. */
  private val complexValue = "complex"

  /** All tests environment value. */
  private val allValue = "all"

  /** Execute simple standard remote API tests for all transport plugins and default codec plugin only. */
  def simple: Boolean =
    complex || Option(System.getenv(environment)).exists(_.toLowerCase == simpleValue)

  /** Execute complex generative remote API tests for all transport plugins and default codec plugin only. */
  def complex: Boolean =
    all || Option(System.getenv(environment)).exists(_.toLowerCase == complexValue)

  /** Execute complex generative remote API tests for all transport plugins and all codec plugins. */
  def all: Boolean =
    Option(System.getenv(environment)).exists(_.toLowerCase == allValue)
}
