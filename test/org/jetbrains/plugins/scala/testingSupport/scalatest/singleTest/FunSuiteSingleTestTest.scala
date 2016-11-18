package org.jetbrains.plugins.scala.testingSupport.scalatest.singleTest

import org.jetbrains.plugins.scala.testingSupport.scalatest.generators.FunSuiteGenerator

/**
 * @author Roman.Shein
 * @since 20.01.2015.
 */
trait FunSuiteSingleTestTest extends FunSuiteGenerator {
  val funSuiteTestPath = List("[root]", funSuiteClassName, "should run single test")
  val funSuiteTaggedTestPath = List("[root]", funSuiteClassName, "tagged")

  def testFunSuite() {
    runTestByLocation(9, 8, funSuiteFileName,
      checkConfigAndSettings(_, funSuiteClassName, "should run single test"),
      root => checkResultTreeHasExactNamedPath(root, funSuiteTestPath:_*) &&
          checkResultTreeDoesNotHaveNodes(root, "should not run other tests")
    )
  }

  def testTaggedFunSuite() {
    runTestByLocation(12, 8, funSuiteFileName,
      checkConfigAndSettings(_, funSuiteClassName, "tagged"),
      root => checkResultTreeHasExactNamedPath(root, funSuiteTaggedTestPath:_*) &&
        checkResultTreeDoesNotHaveNodes(root, "should not run other tests")
    )
  }
}
