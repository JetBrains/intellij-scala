package org.jetbrains.plugins.scala.testingSupport.scalatest.singleTest

import org.jetbrains.plugins.scala.testingSupport.scalatest.generators.FreeSpecPathGenerator

/**
 * @author Roman.Shein
 * @since 20.01.2015.
 */
trait FreeSpecPathSingleTestTest extends FreeSpecPathGenerator {
  val freeSpecPathTestPath = List("[root]", freeSpecPathClassName, "A FreeSpecTest", "should be able to run single test")

  def testFreeSpecPath() {
    runTestByLocation(5, 15, freeSpecPathFileName,
      checkConfigAndSettings(_, freeSpecPathClassName, "A FreeSpecTest should be able to run single test"),
      root => checkResultTreeHasExactNamedPath(root, freeSpecPathTestPath:_*) &&
          checkResultTreeDoesNotHaveNodes(root, "should not run tests that are not selected")
    )
  }
}
