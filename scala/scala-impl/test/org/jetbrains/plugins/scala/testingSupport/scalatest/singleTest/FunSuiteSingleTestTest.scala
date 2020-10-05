package org.jetbrains.plugins.scala.testingSupport.scalatest.singleTest

import org.jetbrains.plugins.scala.testingSupport.scalatest.generators.FunSuiteGenerator

trait FunSuiteSingleTestTest extends FunSuiteGenerator {

  val funSuiteTestPath = TestNodePath("[root]", funSuiteClassName, "should run single test")
  val funSuiteTaggedTestPath = TestNodePath("[root]", funSuiteClassName, "tagged")

  def testFunSuite(): Unit = {
    runTestByLocation(loc(funSuiteFileName, 9, 8),
      assertConfigAndSettings(_, funSuiteClassName, "should run single test"),
      root => {
        assertResultTreeHasExactNamedPath(root, funSuiteTestPath)
        assertResultTreeDoesNotHaveNodes(root, "should not run other tests")
      }
    )
  }

  def testTaggedFunSuite(): Unit = {
    runTestByLocation(loc(funSuiteFileName, 12, 8),
      assertConfigAndSettings(_, funSuiteClassName, "tagged"),
      root => {
        assertResultTreeHasExactNamedPath(root, funSuiteTaggedTestPath)
        assertResultTreeDoesNotHaveNodes(root, "should not run other tests")
      }
    )
  }
}
