package org.jetbrains.plugins.scala.testingSupport.scalatest.singleTest.tagged

import org.jetbrains.plugins.scala.testingSupport.scalatest.generators.FreeSpecGenerator

trait FreeSpecTaggedSingleTestTest extends FreeSpecGenerator {
  val freeSpecTaggedTestPath = List("[root]", freeSpecClassName, "A FreeSpecTest", "can be tagged")

  def testTaggedFreeSpec(): Unit = {
    runTestByLocation2(12, 7, freeSpecFileName,
      assertConfigAndSettings(_, freeSpecClassName, "A FreeSpecTest can be tagged"),
      root => {
        assertResultTreeHasExactNamedPath(root, freeSpecTaggedTestPath)
        assertResultTreeDoesNotHaveNodes(root, "should not run tests that are not selected")
      }
    )
  }
}
