package org.jetbrains.plugins.scala.testingSupport.scalatest.singleTest

import org.jetbrains.plugins.scala.testingSupport.scalatest.generators.FunSuiteGenerator

/**
 * @author Roman.Shein
 * @since 20.01.2015.
 */
trait FunSuiteSingleTestTest extends FunSuiteGenerator {
  def testFunSuite() {
    addFunSuite()

    runTestByLocation(9, 8, "FunSuiteTest.scala",
      checkConfigAndSettings(_, "FunSuiteTest", "should run single test"),
      root => checkResultTreeHasExactNamedPath(root, "[root]", "FunSuiteTest", "should run single test") &&
          checkResultTreeDoesNotHaveNodes(root, "should not run other tests"),
      debug = true
    )
  }
}
