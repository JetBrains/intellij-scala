package org.jetbrains.plugins.scala.testingSupport.scalatest.singleTest

import org.jetbrains.plugins.scala.testingSupport.scalatest.generators.FlatSpecGenerator

/**
 * @author Roman.Shein
 * @since 20.01.2015.
 */
trait FlatSpecSingleTestTest extends FlatSpecGenerator {
  val flatSpecTestPath = List("[root]", "FlatSpecTest", "A FlatSpecTest", "should be able to run single test")

  def testFlatSpec() {
    addFlatSpec()

    runTestByLocation(7, 1, flatSpecFileName,
      checkConfigAndSettings(_, flatSpecClassName, "A FlatSpecTest should be able to run single test"),
      root => checkResultTreeHasExactNamedPath(root, flatSpecTestPath:_*) &&
          checkResultTreeDoesNotHaveNodes(root, "should not run other tests"),
      debug = true
    )
  }
}
