package org.jetbrains.plugins.scala.testingSupport.scalatest.singleTest

import org.jetbrains.plugins.scala.testingSupport.scalatest.generators.FreeSpecPathGenerator

/**
 * @author Roman.Shein
 * @since 20.01.2015.
 */
trait FreeSpecPathSingleTestTest extends FreeSpecPathGenerator {
  def testFreeSpecPath() {
    addPathFreeSpec()

    runTestByLocation(5, 15, "FreeSpecPathTest.scala",
      checkConfigAndSettings(_, "FreeSpecPathTest", "A FreeSpecTest should be able to run single test"),
      root => checkResultTreeHasExactNamedPath(root, "[root]", "FreeSpecPathTest", "A FreeSpecTest", "should be able to run single test") &&
          checkResultTreeDoesNotHaveNodes(root, "should not run tests that are not selected"),
      debug = true
    )
  }
}
