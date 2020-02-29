package org.jetbrains.plugins.scala.testingSupport.scalatest.singleTest

import org.jetbrains.plugins.scala.testingSupport.scalatest.generators.FreeSpecGenerator

trait FreeSpecSingleTestTest extends FreeSpecGenerator {

  val freeSpecTestPath = List("[root]", freeSpecClassName, "A FreeSpecTest", "should be able to run single tests")
  val freeSpecNonNestedTestPath = List("[root]", complexFreeSpecClassName, "Not nested scope")

  def testFreeSpec(): Unit =
    runTestByLocation2(6, 3, freeSpecFileName,
      checkConfigAndSettings(_, freeSpecClassName, "A FreeSpecTest should be able to run single tests"),
      root => checkResultTreeHasExactNamedPath(root, freeSpecTestPath:_*) &&
          checkResultTreeDoesNotHaveNodes(root, "should not run tests that are not selected")
    )

  def testFreeSpecNonNested(): Unit =
    runTestByLocation2(33, 15, complexFreeSpecFileName,
      assertConfigAndSettings(_, complexFreeSpecClassName, "Not nested scope"),
      root => checkResultTreeHasExactNamedPath(root, freeSpecNonNestedTestPath:_*) &&
        checkResultTreeDoesNotHaveNodes(root, "A ComplexFreeSpec Outer scope 2 Inner test")
    )
}
