package org.jetbrains.plugins.scala.testingSupport.scalatest.singleTest

import org.jetbrains.plugins.scala.testingSupport.scalatest.generators.FreeSpecGenerator

/**
 * @author Roman.Shein
 * @since 20.01.2015.
 */
trait FreeSpecSingleTestTest extends FreeSpecGenerator {
  val freeSpecTestPath = List("[root]", freeSpecClassName, "A FreeSpecTest", "should be able to run single tests")

  def testFreeSpec() {
    runTestByLocation(6, 3, freeSpecFileName,
      checkConfigAndSettings(_, freeSpecClassName, "A FreeSpecTest should be able to run single tests"),
      root => checkResultTreeHasExactNamedPath(root, freeSpecTestPath:_*) &&
          checkResultTreeDoesNotHaveNodes(root, "should not run tests that are not selected")
    )
  }
}
