package org.jetbrains.plugins.scala.testingSupport.scalatest.base.singleTest.tagged

import org.jetbrains.plugins.scala.testingSupport.scalatest.base.generators.FreeSpecGenerator

trait FreeSpecTaggedSingleTestTest extends FreeSpecGenerator {

  protected val freeSpecTaggedTestPath = TestNodePath("[root]", freeSpecClassName, "A FreeSpecTest", "can be tagged")

  def testTaggedFreeSpec(): Unit = {
    runTestByLocation(loc(freeSpecFileName, 12, 7),
      assertConfigAndSettings(_, freeSpecClassName, "A FreeSpecTest can be tagged"),
      root => {
        assertResultTreeHasExactNamedPath(root, freeSpecTaggedTestPath)
        assertResultTreeDoesNotHaveNodes(root, "should not run tests that are not selected")
      }
    )
  }
}
