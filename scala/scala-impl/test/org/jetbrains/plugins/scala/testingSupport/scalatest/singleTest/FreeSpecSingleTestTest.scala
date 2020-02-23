package org.jetbrains.plugins.scala.testingSupport.scalatest.singleTest

import org.jetbrains.plugins.scala.testingSupport.scalatest.generators.FreeSpecGenerator

trait FreeSpecSingleTestTest extends FreeSpecGenerator {

  val freeSpecTestPath = List("[root]", freeSpecClassName, "A FreeSpecTest", "should be able to run single tests")

  def testFreeSpec(): Unit = {
    runTestByLocation(6, 3, freeSpecFileName,
      checkConfigAndSettings(_, freeSpecClassName, "A FreeSpecTest should be able to run single tests"),
      root => checkResultTreeHasExactNamedPath(root, freeSpecTestPath:_*) &&
          checkResultTreeDoesNotHaveNodes(root, "should not run tests that are not selected")
    )
  }
}
