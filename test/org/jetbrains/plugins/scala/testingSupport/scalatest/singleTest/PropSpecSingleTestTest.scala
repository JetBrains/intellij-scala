package org.jetbrains.plugins.scala.testingSupport.scalatest.singleTest

import org.jetbrains.plugins.scala.testingSupport.scalatest.generators.PropSpecGenerator

/**
 * @author Roman.Shein
 * @since 20.01.2015.
 */
trait PropSpecSingleTestTest extends PropSpecGenerator {
  val propSpecTestPath = List("[root]", "PropSpecTest", "Single tests should run")

  def testPropSpec() {
    addPropSpec()

    runTestByLocation(5, 5, "PropSpecTest.scala",
      checkConfigAndSettings(_, "PropSpecTest", "Single tests should run"),
      root => checkResultTreeHasExactNamedPath(root, propSpecTestPath:_*) &&
          checkResultTreeDoesNotHaveNodes(root, "other tests should not run"),
      debug = true
    )
  }
}
