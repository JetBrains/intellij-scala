package org.jetbrains.plugins.scala.testingSupport.scalatest.singleTest

import org.jetbrains.plugins.scala.testingSupport.scalatest.generators.FreeSpecPathGenerator

trait FreeSpecPathSingleTestTest extends FreeSpecPathGenerator {

  val freeSpecPathTestPath = TestNodePath("[root]", freeSpecPathClassName, "A FreeSpecTest", "should be able to run single test")

  def testFreeSpecPath(): Unit = {
    runTestByLocation(loc(freeSpecPathFileName, 5, 15),
      assertConfigAndSettings(_, freeSpecPathClassName, "A FreeSpecTest should be able to run single test"),
      root => {
        assertResultTreeHasExactNamedPath(root, freeSpecPathTestPath)
        assertResultTreeDoesNotHaveNodes(root, "should not run tests that are not selected")
      }
    )
  }
}
