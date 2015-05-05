package org.jetbrains.plugins.scala.testingSupport.scalatest.singleTest

import org.jetbrains.plugins.scala.testingSupport.scalatest.generators.FunSpecGenerator

/**
 * @author Roman.Shein
 * @since 20.01.2015.
 */
trait FunSpecSingleTestTest extends FunSpecGenerator {
  val funSpecTestPath = List("[root]", "FunSpecTest", "FunSpecTest", "should launch single test")

  def testFunSpec() {
    addFunSpec()

    runTestByLocation(5, 9, funSpecFileName,
      checkConfigAndSettings(_, funSpecClassName, "FunSpecTest should launch single test"),
      root => checkResultTreeHasExactNamedPath(root, funSpecTestPath:_*) &&
          checkResultTreeDoesNotHaveNodes(root, "should not launch other tests"),
      debug = true
    )
  }
}
