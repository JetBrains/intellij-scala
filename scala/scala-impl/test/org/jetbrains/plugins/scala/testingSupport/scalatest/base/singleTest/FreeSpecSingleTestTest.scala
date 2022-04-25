package org.jetbrains.plugins.scala.testingSupport.scalatest.base.singleTest

import org.jetbrains.plugins.scala.testingSupport.scalatest.base.generators.FreeSpecGenerator

trait FreeSpecSingleTestTest extends FreeSpecGenerator {

  protected val freeSpecTestPath = TestNodePath("[root]", freeSpecClassName, "A FreeSpecTest", "should be able to run single tests")
  protected val freeSpecNonNestedTestPath = TestNodePath("[root]", complexFreeSpecClassName, "Not nested scope")

  def testFreeSpec(): Unit =
    runTestByLocation(loc(freeSpecFileName, 6, 3),
      assertConfigAndSettings(_, freeSpecClassName, "A FreeSpecTest should be able to run single tests"),
      root => {
        assertResultTreeHasExactNamedPath(root, freeSpecTestPath)
        assertResultTreeDoesNotHaveNodes(root, "should not run tests that are not selected")
      }
    )

  def testFreeSpecNonNested(): Unit =
    runTestByLocation(loc(complexFreeSpecFileName, 33, 15),
      assertConfigAndSettings(_, complexFreeSpecClassName, "Not nested scope"),
      root => {
        assertResultTreeHasExactNamedPath(root, freeSpecNonNestedTestPath)
        assertResultTreeDoesNotHaveNodes(root, "A ComplexFreeSpec Outer scope 2 Inner test")
      }
    )
}
