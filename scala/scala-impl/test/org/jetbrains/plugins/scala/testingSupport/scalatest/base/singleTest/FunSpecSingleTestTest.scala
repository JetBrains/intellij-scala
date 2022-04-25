package org.jetbrains.plugins.scala.testingSupport.scalatest.base.singleTest

import org.jetbrains.plugins.scala.testingSupport.scalatest.base.generators.FunSpecGenerator

trait FunSpecSingleTestTest extends FunSpecGenerator {

  protected val funSpecTestPath = TestNodePath("[root]", funSpecClassName, "FunSpecTest", "should launch single test")
  protected val funSpecTaggedTestPath = TestNodePath("[root]", funSpecClassName, "taggedScope", "is tagged")

  def testFunSpec(): Unit = {
    runTestByLocation(loc(funSpecFileName, 5, 9),
      assertConfigAndSettings(_, funSpecClassName, "FunSpecTest should launch single test"),
      root => {
        assertResultTreeHasExactNamedPath(root, funSpecTestPath)
        assertResultTreeDoesNotHaveNodes(root, "should not launch other tests")
      }
    )
  }

  def testTaggedFunSpec(): Unit = {
    runTestByLocation(loc(funSpecFileName, 20, 6),
      assertConfigAndSettings(_, funSpecClassName, "taggedScope is tagged"),
      root => {
        assertResultTreeHasExactNamedPath(root, funSpecTaggedTestPath)
        assertResultTreeDoesNotHaveNodes(root, "should not launch other tests")
      }
    )
  }
}
