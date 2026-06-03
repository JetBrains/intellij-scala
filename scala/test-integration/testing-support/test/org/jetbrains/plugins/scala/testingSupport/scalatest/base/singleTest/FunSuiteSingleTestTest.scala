package org.jetbrains.plugins.scala.testingSupport.scalatest.base.singleTest

import com.intellij.execution.testframework.sm.runner.states.TestStateInfo.Magnitude
import org.jetbrains.plugins.scala.testingSupport.scalatest.base.generators.FunSuiteGenerator

trait FunSuiteSingleTestTest extends FunSuiteGenerator {

  protected val funSuiteTestPath = TestNodePathWithStatus(Magnitude.PASSED_INDEX, "[root]", funSuiteClassName, "should run single test")
  protected val funSuiteTaggedTestPath = TestNodePathWithStatus(Magnitude.PASSED_INDEX, "[root]", funSuiteClassName, "tagged")

  def testFunSuite(): Unit = {
    runTestByLocation(loc(funSuiteFileName, 9, 8),
      assertConfigAndSettings(_, funSuiteClassName, "should run single test"),
      root => {
        assertResultTreeHasSinglePath(root, funSuiteTestPath)
      }
    )
  }

  def testTaggedFunSuite(): Unit = {
    runTestByLocation(loc(funSuiteFileName, 12, 8),
      assertConfigAndSettings(_, funSuiteClassName, "tagged"),
      root => {
        assertResultTreeHasSinglePath(root, funSuiteTaggedTestPath)
      }
    )
  }
}
