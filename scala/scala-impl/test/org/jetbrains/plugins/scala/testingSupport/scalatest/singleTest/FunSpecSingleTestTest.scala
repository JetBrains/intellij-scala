package org.jetbrains.plugins.scala.testingSupport.scalatest.singleTest

import org.jetbrains.plugins.scala.testingSupport.scalatest.generators.FunSpecGenerator

trait FunSpecSingleTestTest extends FunSpecGenerator {

  val funSpecTestPath = List("[root]", funSpecClassName, "FunSpecTest", "should launch single test")
  val funSpecTaggedTestPath = List("[root]", funSpecClassName, "taggedScope", "is tagged")

  def testFunSpec(): Unit = {
    runTestByLocation2(5, 9, funSpecFileName,
      assertConfigAndSettings(_, funSpecClassName, "FunSpecTest should launch single test"),
      root => {
        assertResultTreeHasExactNamedPath(root, funSpecTestPath)
        assertResultTreeDoesNotHaveNodes(root, "should not launch other tests")
      }
    )
  }

  def testTaggedFunSpec(): Unit = {
    runTestByLocation2(20, 6, funSpecFileName,
      assertConfigAndSettings(_, funSpecClassName, "taggedScope is tagged"),
      root => {
        assertResultTreeHasExactNamedPath(root, funSpecTaggedTestPath)
        assertResultTreeDoesNotHaveNodes(root, "should not launch other tests")
      }
    )
  }
}
